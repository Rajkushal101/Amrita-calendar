package acn.amrita.chen.planner.workspace

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import acn.amrita.chen.planner.data.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    fun unwrap(v: Any?): Any? = when(v) {
        null, JSONObject.NULL -> null
        is JSONObject -> v.toMap()
        is JSONArray -> (0 until v.length()).map { unwrap(v.get(it)) }
        else -> v
    }
    unwrap(opt(key))
}
fun json(value: Any?) = JSONObject(value as? Map<*, *> ?: emptyMap<String, Any>())

/** Composition root owns one repository and its listener lifetime. */
class WorkspaceRepository(val context: Context, private val scope: CoroutineScope,private val realtime:Boolean=true) {
    companion object { private val syncMutex=Mutex() }
    val db = AppDatabase.getDatabase(context)
    val dao = db.workspaceDao()
    val auth = FirebaseAuth.getInstance()
    private val cloud = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("asia-south1")
    private val storage = FirebaseStorage.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val groupListeners = mutableMapOf<String, MutableList<ListenerRegistration>>()
    val owner = MutableStateFlow(auth.currentUser?.takeUnless { it.isAnonymous }?.uid ?: "legacy")
    val status = MutableStateFlow("")
    val groups = MutableStateFlow<List<JSONObject>>(emptyList())
    val messages = MutableStateFlow<List<JSONObject>>(emptyList())
    val members = MutableStateFlow<List<JSONObject>>(emptyList())
    val files = MutableStateFlow<List<JSONObject>>(emptyList())
    val groupPreferences=MutableStateFlow<Map<String,JSONObject>>(emptyMap())
    val messageCounts=MutableStateFlow<Map<String,Long>>(emptyMap())
    val uploadProgress = MutableStateFlow<Int?>(null)
    private var activeChat = ""
    private var chatRegistration: ListenerRegistration? = null
    private var memberRegistration: ListenerRegistration? = null
    private var fileRegistration: ListenerRegistration? = null
    private var oldest: com.google.firebase.firestore.DocumentSnapshot? = null
    private var initialized = false
    val cloudProfile=MutableStateFlow(JSONObject())
    private val authListener = FirebaseAuth.AuthStateListener { a ->
        val next = a.currentUser?.takeUnless { it.isAnonymous }?.uid ?: "legacy"
        if(next != owner.value || !initialized) {
            initialized = true
            stopListeners(); owner.value = next; groups.value = emptyList(); messages.value = emptyList();groupPreferences.value=emptyMap();messageCounts.value=emptyMap();cloudProfile.value=JSONObject()
            java.io.File(context.cacheDir,"shared").deleteRecursively()
            if(a.currentUser?.isEmailVerified == true) startListeners(next)
        }
    }
    init { if(realtime)auth.addAuthStateListener(authListener) }
    private fun stopListeners() {
        listeners.forEach { it.remove() }; listeners.clear()
        groupListeners.values.flatten().forEach { it.remove() }; groupListeners.clear()
        closeChat()
    }
    fun close() { stopListeners(); auth.removeAuthStateListener(authListener) }
    fun refreshIdentity() { stopListeners(); if(auth.currentUser?.isEmailVerified == true) startListeners(owner.value) }
    suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): JSONObject {
        check(auth.currentUser?.isEmailVerified == true) { "Sign in and verify your email in Settings first" }
        return json(functions.getHttpsCallable(name).call(data).await().getData())
    }
    internal fun record(user: String, j: JSONObject, group: String = "", category: String = ""): AcademicRecord {
        val kind = if(category.isNotBlank()) category else j.getString("kind")
        val data = if(category.isBlank()) j.getJSONObject("data").apply {
            put("approvedBy", j.optString("approvedBy")); put("sources", j.optJSONArray("sources") ?: JSONArray())
            put("sourceNeedsReview",j.optBoolean("sourceNeedsReview"))
        } else j
        return AcademicRecord(user,j.getString("id"),kind,j.optString("courseId"),group,data.toString(),j.optLong("revision",1),j.optLong("updatedAt",j.optLong("createdAt",System.currentTimeMillis())),j.optBoolean("deleted"))
    }
    private fun startListeners(user: String) {
        registerDevice(context)
        listeners += cloud.document("users/$user/profile/main").addSnapshotListener {s,_ ->
            if(owner.value==user && s?.exists()==true) scope.launch {
                if(dao.pending(user).none{it.operation=="saveProfile"})cloudProfile.value=json(s.data)
            }
        }
        fun listen(path: String, category: String = "", group: String = ""): ListenerRegistration = cloud.collection(path).addSnapshotListener { snapshot, error ->
            if(owner.value != user) return@addSnapshotListener
            if(error != null) { status.value = "Sync unavailable. Cached data is still available."; return@addSnapshotListener }
            if(snapshot != null) scope.launch {
                db.withTransaction {
                    for(d in snapshot.documents) {
                        val j=json(d.data).put("id",d.id)
                        val incoming=record(user,j,group,category)
                        val local=dao.get(user,incoming.id)
                        // Never overwrite a locally pending revision with an older server snapshot.
                        if(local==null || incoming.revision >= local.revision) dao.put(incoming)
                    }
                }
            }
        }
        listeners += listen("users/$user/records")
        listeners += listen("users/$user/proposals","PROPOSAL")
        listeners += cloud.collection("users/$user/groupPreferences").addSnapshotListener{s,_->
            if(owner.value==user&&s!=null)groupPreferences.value=s.documents.associate{it.id to json(it.data)}
        }
        listeners += cloud.collection("users/$user/personal").addSnapshotListener { s,_ ->
            if(owner.value==user && s!=null) scope.launch {
                val queued=dao.pending(user).filter{it.operation=="savePersonal"}.map{JSONObject(it.payload).optString("id")}.toSet()
                s.documents.filter{it.id !in queued}.forEach { dao.put(AcademicRecord(user,"personal:${it.id}","PERSONAL",payload=json(it.data).toString())) }
            }
        }
        listeners += cloud.collection("users/$user/groups").addSnapshotListener { snapshot,error ->
            if(owner.value!=user) return@addSnapshotListener
            if(error!=null) {status.value="Could not refresh group membership";return@addSnapshotListener}
            val all=snapshot?.documents?.map {json(it.data).put("id",it.id)} ?: return@addSnapshotListener
            groups.value=all.filter {it.optString("status")!="removed"}
            val approved=all.filter {it.optString("status")=="approved"}.map {it.getString("id")}.toSet()
            for(g in groupListeners.keys.toList()) if(g !in approved) {
                groupListeners.remove(g)?.forEach {it.remove()}
                if(activeChat==g) closeChat()
                scope.launch {dao.purgeGroup(user,g);java.io.File(context.cacheDir,"shared/$g").deleteRecursively()}
            }
            all.filter {it.optString("status")=="removed"}.forEach {g->scope.launch{dao.purgeGroup(user,g.getString("id"));java.io.File(context.cacheDir,"shared/${g.getString("id")}").deleteRecursively()}}
            for(g in approved) if(g !in groupListeners) groupListeners[g]= mutableListOf(listen("groups/$g/records",group=g),listen("groups/$g/proposals","PROPOSAL",g),
                cloud.document("groups/$g").addSnapshotListener{s,_->if(owner.value==user&&s!=null)messageCounts.value=messageCounts.value+(g to (s.getLong("messageCount")?:0))})
        }
        scope.launch { retryPending() }
    }

    suspend fun saveManual(kind: String, data: JSONObject, courseId: String = "", existing: AcademicRecord? = null, group: String = "") {
        AcademicValidation.validate(kind,data)
        val user=owner.value; val targetId=existing?.id ?: (if(group.isBlank())"" else "${group}_")+UUID.randomUUID().toString()
        val repairing=existing?.kind=="REPAIR"
        val change=JSONObject().put("id",targetId).put("kind",kind).put("courseId",courseId)
            .put("action",if(existing==null||repairing) "ADD" else "UPDATE").put("expectedRevision",if(repairing)0 else existing?.revision ?: 0).put("data",data)
        val args=JSONObject().put("operationId",UUID.randomUUID().toString()).put("groupId",group).put("changes",JSONArray().put(change))
        if(group.isNotBlank()) {
            val result=call("commitRecords",args.toMap()); cacheResult(user,group,result)
        } else db.withTransaction {
            val current=dao.get(user,targetId)
            check(existing==null && current==null || existing!=null && current?.revision==existing.revision) {"Record changed. Reopen and review."}
            if(existing!=null) dao.put(existing.copy(id="history:${existing.id}:${existing.revision}",kind="HISTORY",payload=JSONObject(existing.payload).put("recordId",existing.id).put("recordKind",existing.kind).toString()))
            dao.put(AcademicRecord(user,targetId,kind,courseId,payload=data.toString(),revision=if(repairing)1 else (existing?.revision ?: 0)+1))
            if(user!="legacy") dao.enqueue(PendingWrite(user,id=args.getString("operationId"),operation="commitRecords",payload=args.toString()))
        }
        if(group.isBlank()) retryPending()
    }
    private suspend fun cacheResult(user:String,group:String,result:JSONObject) {
        val rows=result.optJSONArray("records") ?: return
        db.withTransaction {(0 until rows.length()).forEach{
            val incoming=record(user,rows.getJSONObject(it),group)
            if((dao.get(user,incoming.id)?.revision ?: 0)<=incoming.revision)dao.put(incoming)
        }}
    }
    suspend fun retryPending(force:Boolean=false) = syncMutex.withLock {
        val user=owner.value
        if(user=="legacy" || auth.currentUser?.isEmailVerified!=true) return@withLock
        val blockedRecords=mutableSetOf<String>()
        val blockedCourses=mutableSetOf<String>()
        for(write in dao.pending(user)) {
            if(owner.value!=user) return@withLock
            if(isDependentOn(write, blockedRecords, blockedCourses)) continue
            if(!force && write.error.isNotBlank() && isTerminalOrConflictError(write.error)) {
                blockedRecords.addAll(affectedRecordIds(write))
                blockedCourses.addAll(affectedCourseIds(write))
                continue
            }
            try {
                val result=call(write.operation,JSONObject(write.payload).toMap())
                db.withTransaction { if(write.operation=="commitRecords") cacheResult(user,"",result); dao.acknowledge(user,write.id) }
            } catch(e:CancellationException) {throw e}
            catch(e:Exception) {
                val classified=formatClassifiedError(e)
                val userMsg=userFacingError(classified)
                dao.enqueue(write.copy(error=classified))
                val category=errorCategory(classified)
                if(category=="TERMINAL" || category=="CONFLICT") {
                    blockedRecords.addAll(affectedRecordIds(write))
                    blockedCourses.addAll(affectedCourseIds(write))
                    status.value=if(category=="CONFLICT") "A change conflicted with server version. Open Inbox to review." else "Some changes could not be applied: $userMsg"
                    continue
                } else {
                    status.value=if(category=="AUTH") "Account verification needed. Check Settings." else "Some changes are pending. Open Inbox to retry."
                    break
                }
            }
        }
    }
    suspend fun conflictSnapshot(write:PendingWrite):JSONObject {
        check(write.owner==owner.value){"Account changed"}
        val c=JSONObject(write.payload).getJSONArray("changes").getJSONObject(0)
        val s=cloud.document("users/${write.owner}/records/${c.getString("id")}").get(com.google.firebase.firestore.Source.SERVER).await()
        return if(s.exists())json(s.data).put("id",s.id) else JSONObject().put("revision",0)
    }
    suspend fun resolveConflict(
        write: PendingWrite,
        reviewedServer: JSONObject,
        reviewedLocal: AcademicRecord,
        keepLocal: Boolean,
        reviewedPendingIds: Set<String> = emptySet()
    ): Unit = syncMutex.withLock {
        check(write.owner==owner.value){"Account changed"}
        val target=JSONObject(write.payload).getJSONArray("changes").getJSONObject(0).getString("id")
        check(target==reviewedLocal.id){"Target ID mismatch"}
        val latest=conflictSnapshot(write)
        check(latest.optLong("revision")==reviewedServer.optLong("revision")){"Server record changed again. Review it again."}
        applyConflictResolutionTx(db,dao,write.owner,target,write,latest,reviewedLocal,keepLocal,reviewedPendingIds)
    }
    suspend fun discardPending(write:PendingWrite)=syncMutex.withLock {
        check(write.owner==owner.value){"Account changed"}
        applyDiscardPendingTx(db,dao,write.owner,write)
        status.value="Pending change discarded. Record marked local-only and preserved in history."
    }
    suspend fun personal(id:String,value:JSONObject) {
        val user=owner.value
        db.withTransaction {
            dao.put(AcademicRecord(user,"personal:$id","PERSONAL",payload=value.toString()))
            if(user!="legacy")dao.enqueue(PendingWrite(user,operation="savePersonal",payload=JSONObject().put("id",id).put("data",value).toString()))
        }
        retryPending()
    }
    suspend fun saveProfile(value:JSONObject) {
        if(owner.value!="legacy") {dao.enqueue(PendingWrite(owner.value,operation="saveProfile",payload=JSONObject().put("data",value).toString()));retryPending()}
    }
    suspend fun cacheProposal(j:JSONObject,group:String) {dao.put(record(owner.value,j,group,"PROPOSAL"))}
    suspend fun approve(proposal:AcademicRecord,changes:JSONArray,questionsResolved:Boolean) {
        check(!proposal.json().optBoolean("sourceNeedsReview")) {"Source changed. Extract again."}
        check(questionsResolved || (proposal.json().optJSONArray("questions")?.length()?:0)==0) {"Resolve the questions before approval"}
        val result=call("applyProposal",mapOf("groupId" to proposal.groupId,"proposalId" to proposal.id,"expectedRevision" to proposal.revision,
            "operationId" to "apply_${proposal.id}_${proposal.revision}","questionsResolved" to questionsResolved,
            "changes" to (0 until changes.length()).map {changes.getJSONObject(it).toMap()}))
        cacheResult(owner.value,proposal.groupId,result)
        dao.put(proposal.copy(payload=proposal.json().put("status","applied").toString(),revision=proposal.revision+1))
    }
    suspend fun reject(proposal:AcademicRecord) {
        call("rejectProposal",mapOf("groupId" to proposal.groupId,"id" to proposal.id))
        dao.put(proposal.copy(payload=proposal.json().put("status","rejected").toString(),revision=proposal.revision+1))
    }
    fun openChat(group:String) {
        closeChat();activeChat=group;val user=owner.value
        chatRegistration=cloud.collection("groups/$group/messages").orderBy("createdAt",Query.Direction.DESCENDING).limit(50).addSnapshotListener {s,e->
            if(activeChat!=group || owner.value!=user)return@addSnapshotListener
            if(e!=null){messages.value=emptyList();status.value="Group messages unavailable. Check membership and connection.";return@addSnapshotListener}
            s?.let {snap->
                if(oldest==null) oldest=snap.documents.lastOrNull()
                val incoming=snap.documents.map {json(it.data).put("id",it.id)}
                val incomingIds=incoming.map {it.getString("id")}.toSet()
                val retainedOlder=messages.value.filter {it.optString("id") !in incomingIds}
                messages.value=(retainedOlder+incoming).distinctBy {it.getString("id")}.sortedBy {it.optLong("createdAt")}
            }
        }
        memberRegistration=cloud.collection("groups/$group/members").addSnapshotListener {s,_->if(activeChat==group)members.value=s?.documents?.map {json(it.data).put("id",it.id)}?:emptyList()}
        fileRegistration=cloud.collection("groups/$group/attachments").addSnapshotListener {s,_->if(activeChat==group)files.value=s?.documents?.map {json(it.data).put("id",it.id)}?:emptyList()}
    }
    suspend fun loadOlder() {
        val group=activeChat;val cursor=oldest ?: return
        val s=cloud.collection("groups/$group/messages").orderBy("createdAt",Query.Direction.DESCENDING).startAfter(cursor).limit(50).get().await()
        if(activeChat!=group)return
        val olderDocs=s.documents.map {json(it.data).put("id",it.id)}
        if(olderDocs.isNotEmpty()){
            oldest=s.documents.lastOrNull()
            messages.value=(olderDocs+messages.value).distinctBy {it.getString("id")}.sortedBy {it.optLong("createdAt")}
        }
    }
    fun closeChat() {chatRegistration?.remove();memberRegistration?.remove();fileRegistration?.remove();activeChat="";messages.value=emptyList();members.value=emptyList();files.value=emptyList();oldest=null}
    suspend fun sendMessage(group:String,text:String,attachmentIds:List<String>,replyTo:String) {
        val messageId=UUID.randomUUID().toString()
        val data=JSONObject().put("id",messageId).put("groupId",group).put("text",text).put("attachmentIds",JSONArray(attachmentIds)).put("replyTo",replyTo)
        dao.enqueue(PendingWrite(owner.value,id=messageId,operation="sendMessage",payload=data.toString()))
        retryPending()
    }
    suspend fun upload(uri:Uri,group:String="",course:String="",forAi:Boolean=false):JSONObject {
        check(auth.currentUser?.isEmailVerified==true) {"Verify your account before uploading"}
        val mime=context.contentResolver.getType(uri)?:""
        require(mime in listOf("application/pdf","image/jpeg","image/png","image/webp","text/plain")) {"Use PDF, JPEG, PNG, WebP or TXT"}
        val name=context.contentResolver.query(uri,null,null,null,null)?.use {c->
            if(c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else "Attachment"
        } ?: "Attachment"
        // Bound reads before upload, including providers which omit SIZE metadata.
        val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)!!.use {input->
            val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=25*1024*1024){"File must be at most 25 MB"};output.write(buffer,0,count)};output.toByteArray()
        }}
        require(bytes.isNotEmpty() && bytes.size<=25*1024*1024) {"File must be at most 25 MB"}
        if(mime=="application/pdf"&&forAi) withContext(Dispatchers.IO) {
            val temp=java.io.File.createTempFile("acn_check_",".pdf",context.cacheDir)
            try {temp.writeBytes(bytes);android.os.ParcelFileDescriptor.open(temp,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use {fd->android.graphics.pdf.PdfRenderer(fd).use {require(it.pageCount<=50){"PDF extraction supports at most 50 pages"}}}} finally {temp.delete()}
        }
        val user=owner.value;val attachment=UUID.randomUUID().toString();val root=if(group.isBlank())"users/$user" else "groups/$group"
        call("prepareUpload",mapOf("id" to attachment,"groupId" to group,"size" to bytes.size,"mime" to mime))
        val task=storage.reference.child("$root/uploads/$user/$attachment").putBytes(bytes,com.google.firebase.storage.StorageMetadata.Builder().setContentType(mime).build())
        uploadProgress.value=0
        task.addOnProgressListener {uploadProgress.value=if(it.totalByteCount>0)(100*it.bytesTransferred/it.totalByteCount).toInt() else 0}
        try {task.await();check(owner.value==user){"Account changed; upload was not registered"};return call("registerAttachment",mapOf("id" to attachment,"groupId" to group,"name" to name,"courseId" to course))}
        catch(e:CancellationException){task.cancel();throw e} finally {uploadProgress.value=null}
    }
    suspend fun openAttachment(file:JSONObject):java.io.File {
        val bytes=storage.reference.child(file.getString("path")).getBytes(25L*1024*1024).await()
        val namespace=file.getString("path").split('/').take(2).last().replace(Regex("[^a-zA-Z0-9_-]"),"_")
        return withContext(Dispatchers.IO){val out=java.io.File(context.cacheDir,"shared/$namespace/${file.getString("id")}/${file.getString("name").replace(Regex("[^a-zA-Z0-9._ -]"),"_")}");out.parentFile?.mkdirs();out.writeBytes(bytes);out}
    }
    suspend fun sourceDetail(group:String,source:JSONObject):JSONObject {
        if(source.optString("type")=="text")return JSONObject().put("text",source.optString("excerpt","Original text unavailable"))
        val root=if(group.isBlank())"users/${owner.value}"else"groups/$group"
        val collection=if(source.optString("type")=="message")"messages"else"attachments"
        val s=cloud.document("$root/$collection/${source.getString("id")}").get(com.google.firebase.firestore.Source.SERVER).await()
        check(s.exists()){ "Source is no longer available" }
        return json(s.data).put("id",s.id)
    }
    suspend fun groupPreference(group:String,muted:Boolean) {cloud.document("users/${owner.value}/groupPreferences/$group").set(mapOf("muted" to muted),com.google.firebase.firestore.SetOptions.merge()).await()}
    suspend fun markRead(group:String,sequence:Long) {
        if(sequence<=(groupPreferences.value[group]?.optLong("lastReadSequence")?:0))return
        cloud.document("users/${owner.value}/groupPreferences/$group").set(mapOf("lastReadSequence" to sequence,"lastReadAt" to System.currentTimeMillis()),com.google.firebase.firestore.SetOptions.merge()).await()
    }
    suspend fun history(row:AcademicRecord):List<JSONObject> {
        if(owner.value=="legacy")return dao.all(owner.value).filter{it.kind=="HISTORY"&&it.json().optString("recordId")==row.id}.map{
            JSONObject().put("revision",it.revision).put("data",it.json()).put("actor","This device")
        }.sortedByDescending{it.optLong("revision")}
        val root=if(row.groupId.isBlank())"users/${owner.value}"else"groups/${row.groupId}"
        return cloud.collection("$root/revisions").whereEqualTo("id",row.id).get().await().documents.map{json(it.data)}.sortedByDescending{it.optLong("revision")}
    }

    /** Explicit import preserves legacy IDs via a stable map. It never publishes to a group. */
    suspend fun importLegacy() {
        val user=owner.value
        if(dao.get(user,"legacy_import")!=null)return
        val imported=mutableListOf<AcademicRecord>()
        suspend fun preserve(row:AcademicRecord) {
            if(dao.get(user,row.id)==null){dao.put(row);imported+=row}
        }
        db.withTransaction {
        val subjects=db.subjectDao().getAllSubjectsSync()
        for(s in subjects) {
            val course="legacy-course-${s.id}"
            if(dao.get(user,course)==null) preserve(AcademicRecord(user,course,"COURSE",payload=JSONObject().put("title",s.name).put("code",s.code).put("semester",s.semester.coerceIn(1,12)).put("legacySubjectId",s.id).toString()))
            if(dao.get(user,"personal:$course")==null)preserve(AcademicRecord(user,"personal:$course","PERSONAL",payload=JSONObject().put("attended",s.attendedClasses.coerceIn(0,s.totalClasses.coerceAtLeast(0))).put("total",s.totalClasses.coerceAtLeast(0)).toString()))
            val units=db.subjectSyllabusDao().getUnitsForSubjectSync(s.id)
            if(units.isNotEmpty()) {
                val topics=JSONArray();val completed=JSONArray()
                for(u in units)for(t in db.subjectSyllabusDao().getTopicsForUnitsSync(listOf(u.id))) {
                    topics.put(JSONObject().put("id","legacy-topic-${t.id}").put("unit",u.title).put("title",t.title))
                    if(t.isCompleted)completed.put("legacy-topic-${t.id}")
                }
                preserve(AcademicRecord(user,"personal:legacy-syllabus-${s.id}","PERSONAL",payload=JSONObject().put("completed",completed).toString()))
                preserve(AcademicRecord(user,"legacy-syllabus-${s.id}","SYLLABUS",course,payload=JSONObject().put("title","Full syllabus").put("topics",topics).toString()))
            }
            db.subjectSyllabusDao().getProjectForSubjectSync(s.id)?.let {p->preserve(AcademicRecord(user,"legacy-project-${p.id}","PROJECT",course,payload=JSONObject().put("title",p.title).put("content",p.description).toString()))}
        }
        for(a in db.assignmentDao().getAllAssignments().first()) preserve(AcademicRecord(user,"legacy-assignment-${a.id}","ASSIGNMENT","legacy-course-${a.subjectId}",payload=JSONObject().put("title",a.title).put("content",a.description).put("date",java.time.Instant.ofEpochMilli(a.dueDateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()).toString()))
        for(e in db.eventDao().getAllEvents().first()) preserve(AcademicRecord(user,"legacy-event-${e.id}","NOTICE",payload=JSONObject().put("title",e.title).put("date",java.time.Instant.ofEpochMilli(e.dateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()).put("content",e.notes ?: "").toString()))
        for(s in db.classSessionDao().getAllSessions().first()) if(subjects.any {it.id==s.subjectId}) preserve(AcademicRecord(user,"legacy-session-${s.id}","TIMETABLE","legacy-course-${s.subjectId}",payload=JSONObject().put("title",subjects.first {it.id==s.subjectId}.name).put("day",s.dayOfWeek).put("time","%02d:%02d".format(s.startTimeMinutes/60,s.startTimeMinutes%60)).put("endTime","%02d:%02d".format(s.endTimeMinutes/60,s.endTimeMinutes%60)).put("room",s.room).toString()))
        for(chat in db.chatMessageDao().getAllMessages().first()) if(chat.type in listOf("USER","AI")) preserve(AcademicRecord(user,"legacy-chat-${chat.id}","CHAT",payload=JSONObject().put("title",chat.content).put("role",if(chat.type=="USER")"user"else"assistant").toString(),updatedAt=chat.timestampMillis))
        // Queue parents before their dependants; invalid old rows remain visible for repair.
        for(row in imported) {
            if(row.kind=="CHAT")continue
            if(row.kind=="PERSONAL") {
                if(user!="legacy")dao.enqueue(PendingWrite(user,operation="savePersonal",payload=JSONObject().put("id",row.id.removePrefix("personal:")).put("data",row.json()).toString()))
                continue
            }
            try {
                AcademicValidation.validate(row.kind,row.json())
                if(row.kind !in listOf("COURSE","NOTICE","STUDY_SESSION"))check(dao.get(user,row.courseId)?.kind=="COURSE")
            }catch(e:Exception){dao.put(row.copy(kind="REPAIR",payload=row.json().put("recordKind",row.kind).toString()));continue}
            if(user=="legacy")continue
            val change=JSONObject().put("id",row.id).put("kind",row.kind).put("courseId",row.courseId).put("action","ADD").put("expectedRevision",0).put("data",row.json())
            val operation=UUID.randomUUID().toString()
            val args=JSONObject().put("operationId",operation).put("groupId","").put("changes",JSONArray().put(change))
            dao.enqueue(PendingWrite(user,id=operation,operation="commitRecords",payload=args.toString()))
        }
        dao.put(AcademicRecord(user,"legacy_import","MIGRATION",payload="{\"title\":\"Legacy records imported privately\"}"))
        }
        retryPending()
    }
}

fun formatClassifiedError(e:Exception):String {
    return when(e) {
        is com.google.firebase.functions.FirebaseFunctionsException -> {
            val codeName = e.code.name
            val message = e.message?.take(250) ?: "Backend operation failed"
            when(e.code) {
                com.google.firebase.functions.FirebaseFunctionsException.Code.ABORTED ->
                    "[CONFLICT:$codeName] $message"
                com.google.firebase.functions.FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED,
                com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND,
                com.google.firebase.functions.FirebaseFunctionsException.Code.ALREADY_EXISTS,
                com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                com.google.firebase.functions.FirebaseFunctionsException.Code.OUT_OF_RANGE ->
                    "[TERMINAL:$codeName] $message"
                com.google.firebase.functions.FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    "[AUTH:$codeName] $message"
                else ->
                    "[TRANSIENT:$codeName] $message"
            }
        }
        is com.google.firebase.auth.FirebaseAuthException ->
            "[AUTH:FIREBASE_AUTH] Account operation failed. Check credentials, verification and enabled sign-in methods."
        is IllegalArgumentException ->
            "[TERMINAL:ILLEGAL_ARGUMENT] ${e.message?.take(250) ?: "Check the input"}"
        is IllegalStateException ->
            "[TERMINAL:ILLEGAL_STATE] ${e.message?.take(250) ?: "Check the input"}"
        else -> {
            val rawMsg = e.message ?: ""
            if(rawMsg.contains("CONFLICT", ignoreCase = true)) {
                "[CONFLICT:ABORTED] ${rawMsg.take(250)}"
            } else {
                "[TRANSIENT:IO] Operation failed. Check connection and retry. No success has been assumed."
            }
        }
    }
}

fun errorCategory(error:String):String {
    val trimmed = error.trim()
    val match = Regex("^\\[([A-Z_]+):[A-Z_0-9]+\\]").find(trimmed)
    if(match != null) return match.groupValues[1]
    val lower = trimmed.lowercase()
    if(lower.startsWith("conflict:") || lower.contains("conflict")) return "CONFLICT"
    if(lower.contains("permission-denied") || lower.contains("invalid-argument") ||
       lower.contains("not-found") || lower.contains("already-exists") ||
       lower.contains("failed-precondition") || lower.contains("out-of-range") ||
       lower.contains("check the input") || lower.contains("unsupported")) return "TERMINAL"
    if(lower.contains("unauthenticated") || lower.contains("account operation failed")) return "AUTH"
    return "TRANSIENT"
}

fun userFacingError(error:String):String {
    val trimmed = error.trim()
    val match = Regex("^\\[[A-Z_]+:[A-Z_0-9]+\\]\\s*").find(trimmed)
    return if(match != null) trimmed.substring(match.range.last + 1) else trimmed
}

fun safeError(e:Exception):String = userFacingError(formatClassifiedError(e))

fun isTerminalError(e:Exception):Boolean = errorCategory(formatClassifiedError(e)) == "TERMINAL"
fun isConflictError(e:Exception):Boolean = errorCategory(formatClassifiedError(e)) == "CONFLICT"
fun isTerminalOrConflictError(e:Exception):Boolean = isTerminalError(e) || isConflictError(e)

fun isTerminalError(error:String):Boolean = errorCategory(error) == "TERMINAL"
fun isConflictError(error:String):Boolean = errorCategory(error) == "CONFLICT"
fun isTerminalOrConflictError(error:String):Boolean = errorCategory(error).let { it == "TERMINAL" || it == "CONFLICT" }

fun affectedRecordIds(write:PendingWrite):Set<String> {
    val ids=mutableSetOf<String>()
    try {
        val json=JSONObject(write.payload)
        if(write.operation=="commitRecords") {
            val changes=json.optJSONArray("changes")?:JSONArray()
            for(i in 0 until changes.length()) {
                val c=changes.getJSONObject(i)
                val id=c.optString("id")
                if(id.isNotBlank()) ids.add(id)
            }
        } else {
            val id=json.optString("id")
            if(id.isNotBlank()) ids.add(id)
        }
    }catch(_:Exception){}
    return ids
}

fun affectedCourseIds(write:PendingWrite):Set<String> {
    val courses=mutableSetOf<String>()
    try {
        val json=JSONObject(write.payload)
        if(write.operation=="commitRecords") {
            val changes=json.optJSONArray("changes")?:JSONArray()
            for(i in 0 until changes.length()) {
                val c=changes.getJSONObject(i)
                if(c.optString("kind")=="COURSE") {
                    val id=c.optString("id")
                    if(id.isNotBlank()) courses.add(id)
                }
                val cid=c.optString("courseId")
                if(cid.isNotBlank()) courses.add(cid)
            }
        } else {
            val cid=json.optString("courseId")
            if(cid.isNotBlank()) courses.add(cid)
        }
    }catch(_:Exception){}
    return courses
}

fun isDependentOn(write:PendingWrite, blockedRecords:Set<String>, blockedCourses:Set<String>):Boolean {
    if(blockedRecords.isEmpty() && blockedCourses.isEmpty()) return false
    val ids=affectedRecordIds(write)
    if(ids.any{it in blockedRecords}) return true
    try {
        val json=JSONObject(write.payload)
        if(write.operation=="commitRecords") {
            val changes=json.optJSONArray("changes")?:JSONArray()
            for(i in 0 until changes.length()) {
                val c=changes.getJSONObject(i)
                val cid=c.optString("courseId")
                if(cid.isNotBlank() && (cid in blockedCourses || cid in blockedRecords)) return true
            }
        } else {
            val cid=json.optString("courseId")
            if(cid.isNotBlank() && (cid in blockedCourses || cid in blockedRecords)) return true
        }
    }catch(_:Exception){}
    return false
}

suspend fun applyConflictResolutionTx(
    db: AppDatabase,
    dao: WorkspaceDao,
    owner: String,
    target: String,
    write: PendingWrite,
    latestServer: JSONObject,
    reviewedLocal: AcademicRecord,
    keepLocal: Boolean,
    reviewedPendingIds: Set<String> = emptySet()
) {
    db.withTransaction {
        val currentLocal = dao.get(owner, target) ?: error("Local record unavailable")
        check(currentLocal.payload == reviewedLocal.payload && currentLocal.updatedAt == reviewedLocal.updatedAt && currentLocal.revision == reviewedLocal.revision) {
            "Local record was modified during review. Review again."
        }
        val currentPending = dao.pending(owner).filter { target in affectedRecordIds(it) }
        if (reviewedPendingIds.isNotEmpty()) {
            val unreviewed = currentPending.filter { it.id !in reviewedPendingIds }
            check(unreviewed.isEmpty()) { "Newer unreviewed edits are queued for this record. Review again." }
        }
        val toRetire = if (reviewedPendingIds.isNotEmpty()) currentPending.filter { it.id in reviewedPendingIds } else currentPending
        dao.put(currentLocal.copy(id = "history:${currentLocal.id}:recovery:${UUID.randomUUID()}", kind = "HISTORY"))
        toRetire.forEach { dao.acknowledge(owner, it.id) }
        dao.acknowledge(owner, write.id)
        if (keepLocal) {
            check(!latestServer.optBoolean("deleted")) { "This record was deleted remotely. Keep server version and create a new record if needed." }
            val revision = latestServer.optLong("revision")
            val change = JSONObject().put("id", target).put("kind", currentLocal.kind).put("courseId", currentLocal.courseId).put("action", if (revision == 0L) "ADD" else "UPDATE").put("expectedRevision", revision).put("data", currentLocal.json())
            val operation = UUID.randomUUID().toString()
            dao.put(currentLocal.copy(revision = revision + 1))
            dao.enqueue(PendingWrite(owner, id = operation, operation = "commitRecords", payload = JSONObject().put("operationId", operation).put("groupId", "").put("changes", JSONArray().put(change)).toString()))
        } else {
            val rec = if (latestServer.has("id")) {
                val kind = latestServer.optString("kind", "COURSE")
                val data = latestServer.optJSONObject("data") ?: JSONObject()
                AcademicRecord(owner, latestServer.getString("id"), kind, latestServer.optString("courseId"), latestServer.optString("groupId"), data.toString(), latestServer.optLong("revision", 1), latestServer.optLong("updatedAt", System.currentTimeMillis()), latestServer.optBoolean("deleted"))
            } else currentLocal.copy(deleted = true)
            dao.put(rec)
        }
    }
}

suspend fun applyDiscardPendingTx(
    db: AppDatabase,
    dao: WorkspaceDao,
    owner: String,
    write: PendingWrite
) {
    val affected = affectedRecordIds(write)
    db.withTransaction {
        for (targetId in affected) {
            val currentLocal = dao.get(owner, targetId)
            if (currentLocal != null) {
                dao.put(currentLocal.copy(id = "history:${currentLocal.id}:discarded:${UUID.randomUUID()}", kind = "HISTORY"))
                val payloadJson = currentLocal.json().put("localOnly", true).put("syncStatus", "discarded")
                dao.put(currentLocal.copy(payload = payloadJson.toString()))
            }
        }
        dao.acknowledge(owner, write.id)
    }
}
