package acn.amrita.chen.planner.workspace

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import acn.amrita.chen.planner.ai.ApiKeyManager
import acn.amrita.chen.planner.data.AumsScraper
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

interface AiProvider {
    suspend fun validate(key:String):List<String>
    suspend fun run(key:String,model:String,text:String,mode:String,group:String,messageIds:List<String>,attachments:List<String>):JSONObject
}
class GeminiRelayProvider(private val repo:WorkspaceRepository):AiProvider {
    override suspend fun validate(key:String):List<String> {
        val models=repo.call("validateAiConnection",mapOf("apiKey" to key)).getJSONArray("models")
        return (0 until models.length()).map {models.getString(it)}
    }
    override suspend fun run(key:String,model:String,text:String,mode:String,group:String,messageIds:List<String>,attachments:List<String>) =
        repo.call("runAgent",mapOf("apiKey" to key,"model" to model,"text" to text,"mode" to mode,"groupId" to group,
            "messageIds" to messageIds,"attachmentIds" to attachments,"id" to UUID.randomUUID().toString(),
            "history" to if(group.isBlank())repo.dao.all(repo.owner.value).filter{it.kind=="CHAT"}.sortedBy{it.updatedAt}.takeLast(8).map{mapOf("role" to it.json().optString("role"),"text" to it.title.take(8000))}else emptyList<Map<String,String>>()))
}

data class AgentContext(val courseId:String="",val groupId:String="",val messageIds:List<String> = emptyList(),val attachmentIds:List<String> = emptyList())
object SharedImports { val uris=MutableStateFlow<List<Uri>>(emptyList());val text=MutableStateFlow("") }

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModel(app:Application):AndroidViewModel(app) {
    val repo=WorkspaceRepository(app,viewModelScope)
    val owner=repo.owner
    val records=owner.flatMapLatest {repo.dao.observe(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val pending=owner.flatMapLatest {repo.dao.observePending(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val message=MutableStateFlow("")
    val busy=MutableStateFlow(false)
    val agentStage=MutableStateFlow("")
    val agentAnswer=MutableStateFlow("")
    val availableModels=MutableStateFlow<List<String>>(emptyList())
    val selectedModel=MutableStateFlow("")
    val profile=MutableStateFlow(JSONObject())
    val hasKey=MutableStateFlow(ApiKeyManager.hasApiKey(app))
    val semester=MutableStateFlow(3)
    val now=flow {while(true){emit(java.time.LocalDateTime.now());delay(15000)}}.stateIn(viewModelScope,SharingStarted.Eagerly,java.time.LocalDateTime.now())
    private val provider:AiProvider=DirectGeminiProvider(app, repo)
    private var agentJob:Job?=null
    private val prefs=app.getSharedPreferences("workspace_preferences",0)
    init {
        viewModelScope.launch {repo.cloudProfile.collect{value->if(value.has("semester")){
            profile.value=value;semester.value=value.optInt("semester",3)
            prefs.edit().putString("profile_${owner.value}",value.toString()).apply()
        }}}
        viewModelScope.launch {
            records.collect {all->
                for(r in all.filter{it.kind in listOf("MIDTERM","END_SEMESTER","QUIZ","ASSIGNMENT","PROJECT","STUDY_SESSION","NOTICE")}) {
                    val p=all.find{it.id=="personal:${r.id}"}?.json()
                    val minutes=if(p?.has("reminder")==true&&!p.isNull("reminder"))p.optInt("reminder") else if(r.groupId.isBlank()&&r.json().has("reminderMinutes"))r.json().optInt("reminderMinutes")else null
                    val signature="${r.revision}:$minutes:${r.json().optString("date")}:${r.json().optString("time")}";val cacheKey="reminder_${owner.value}_${r.id}"
                    if(prefs.getString(cacheKey,"")!=signature){AcademicReminders.schedule(getApplication(),owner.value,r,minutes);prefs.edit().putString(cacheKey,signature).apply()}
                }
            }
        }
        viewModelScope.launch {owner.collect {u->
            agentJob?.cancel();agentAnswer.value="";agentStage.value="";availableModels.value=emptyList()
            profile.value=JSONObject(prefs.getString("profile_$u","{}") ?: "{}")
            semester.value=profile.value.optInt("semester",3)
            val savedModel=prefs.getString("model_$u","")?:""
            selectedModel.value=if(savedModel.isNotBlank() && !savedModel.contains("2.5")) savedModel else "gemini-3.6-flash"
            // Seed academic calendar AY2026-27 on first launch for this owner
            acn.amrita.chen.planner.data.AcademicCalendarSeeder.seedIfNeeded(getApplication(), repo.dao, u)
        }}
        viewModelScope.launch {repo.status.collect {if(it.isNotBlank())message.value=it}}
    }
    override fun onCleared(){repo.close();super.onCleared()}
    fun action(block:suspend ()->Unit){viewModelScope.launch{busy.value=true;try{block()}catch(e:CancellationException){throw e}catch(e:Exception){message.value=safeError(e)}finally{busy.value=false}}}
    fun login(email:String,password:String,register:Boolean) = action {
        if(register) {
            val current=repo.auth.currentUser
            if(current?.isAnonymous==true)current.linkWithCredential(EmailAuthProvider.getCredential(email.trim(),password)).await()
            else repo.auth.createUserWithEmailAndPassword(email.trim(),password).await()
            repo.auth.currentUser?.sendEmailVerification()?.await();message.value="Verification email sent. Verify it, then tap Refresh verification."
        } else {repo.auth.signInWithEmailAndPassword(email.trim(),password).await();repo.refreshIdentity();message.value=if(repo.auth.currentUser?.isEmailVerified==true)"Signed in" else "Verify your email before using groups"}
    }
    fun verify()=action{repo.auth.currentUser?.reload()?.await();repo.auth.currentUser?.getIdToken(true)?.await();repo.refreshIdentity();message.value=if(repo.auth.currentUser?.isEmailVerified==true)"Email verified" else "Email is not verified yet"}
    fun resend()=action{repo.auth.currentUser?.sendEmailVerification()?.await();message.value="Verification email sent"}
    fun reset(email:String)=action{repo.auth.sendPasswordResetEmail(email.trim()).await();message.value="If the account exists, a reset email has been sent"}
    fun signOut()=action{
        agentJob?.cancel();androidx.work.WorkManager.getInstance(getApplication()).cancelAllWorkByTag("reminders-${owner.value}")
        val device=getApplication<Application>().getSharedPreferences("device_identity",0).getString("id",null)
        if(device!=null&&repo.auth.currentUser?.isEmailVerified==true)FirebaseFirestore.getInstance().document("users/${owner.value}/devices/$device").delete()
        ApiKeyManager.clearApiKey(getApplication());hasKey.value=false;repo.auth.signOut();message.value="Signed out; the device API key was removed"
    }
    fun saveProfile(data:JSONObject)=action {
        val u=owner.value;require(data.optInt("semester") in 1..12){"Choose semester 1–12"}
        prefs.edit().putString("profile_$u",data.toString()).apply();profile.value=data;semester.value=data.getInt("semester")
        repo.saveProfile(data)
        message.value="Profile saved"
    }
    fun connect(key:String)=action {
        val models=provider.validate(key);check(models.isNotEmpty()){ "No supported models available to this key" }
        ApiKeyManager.saveApiKey(getApplication(),key);hasKey.value=true;availableModels.value=models
        selectedModel.value=models.firstOrNull { it == "gemini-3.6-flash" }
            ?: models.firstOrNull { it.contains("3.6") }
            ?: models.firstOrNull { it.contains("3.5") }
            ?: models.firstOrNull { it.contains("flash") && !it.contains("preview") }
            ?: models.first()
        prefs.edit().putString("model_${owner.value}",selectedModel.value).apply();message.value="Connected. Select the model to use."
    }
    fun selectModel(model:String){selectedModel.value=model;prefs.edit().putString("model_${owner.value}",model).apply()}
    fun clearKey()=action{ApiKeyManager.clearApiKey(getApplication());hasKey.value=false;message.value="API key removed"}
    fun clearChat()=action{
        repo.dao.clearChat(owner.value)
        agentAnswer.value=""
        agentStage.value=""
        message.value="Chat history cleared"
    }
    fun save(kind:String,data:JSONObject,course:String="",existing:AcademicRecord?=null,group:String="")=action{
        repo.saveManual(kind,data,course,existing,group);message.value=if(group.isBlank()&&pending.value.isNotEmpty())"Saved locally; synchronization pending" else "Saved"
    }
    fun personal(id:String,data:JSONObject)=action{repo.personal(id,data)}
    fun createGroup(name:String)=action{val g=repo.call("createGroup",mapOf("id" to UUID.randomUUID().toString(),"name" to name));message.value="Group created. Invitation code: ${g.getString("code")} (7 days)"}
    fun joinGroup(code:String)=action{repo.call("requestJoin",mapOf("code" to code.trim()));message.value="Join request sent. An administrator must approve it."}
    fun manage(group:String,user:String,action:String,role:String="member")=this.action{repo.call("manageMember",mapOf("groupId" to group,"uid" to user,"action" to action,"role" to role));message.value="Membership updated"}
    fun rotate(group:String)=action{val r=repo.call("rotateInvite",mapOf("groupId" to group));message.value="New invitation code: ${r.getString("code")} (7 days)"}
    fun send(group:String,text:String,files:List<String>,reply:String)=action{repo.sendMessage(group,text,files,reply)}
    fun messageAction(group:String,id:String,action:String,extra:Map<String,Any?> = emptyMap())=this.action{repo.call("editMessage",mapOf("groupId" to group,"id" to id,"action" to action)+extra)}
    fun approve(proposal:AcademicRecord,changes:JSONArray,resolved:Boolean)=action{repo.approve(proposal,changes,resolved);message.value="Reviewed changes applied"}
    fun reject(proposal:AcademicRecord)=action{repo.reject(proposal);message.value="Proposal rejected"}
    fun importLegacy()=action{repo.importLegacy();message.value="Existing records imported into this private workspace"}
    fun retry()=action{repo.retryPending(force=true)}
    fun discardPending(write:PendingWrite)=action{repo.discardPending(write);message.value="Pending write discarded"}
    fun runAgent(text:String,extract:Boolean,context:AgentContext,uris:List<Uri>) {
        if(agentJob?.isActive==true)return
        agentJob=viewModelScope.launch {
            try {
                check(hasKey.value){"Connect your personal Gemini key in Settings"}
                val modelToUse=if(selectedModel.value.isNotBlank() && !selectedModel.value.contains("2.5")) selectedModel.value else "gemini-3.6-flash"
                agentStage.value="Reading selected sources"
                val user=owner.value
                val files=context.attachmentIds.toMutableList()
                for(uri in uris){
                    ensureActive()
                    agentStage.value="Reading attachment"
                    if(context.groupId.isNotBlank() && repo.auth.currentUser?.isEmailVerified == true) {
                        try { files+=repo.upload(uri,context.groupId,context.courseId,forAi=true).getString("id") } catch(e:Exception){ Log.w("Agent","Attachment upload skipped: ${e.message}") }
                    }
                }
                val course=records.value.find{it.id==context.courseId}
                val request=if(course!=null)"Selected course ID: ${course.id}; code: ${course.json().optString("code")}.\n$text" else text
                val fileNames = JSONArray()
                for (uri in uris) {
                    val name = runCatching {
                        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) c.getString(idx) else null
                            } else null
                        }
                    }.getOrNull() ?: uri.lastPathSegment ?: "Attached file"
                    fileNames.put(name)
                }
                val userPayload = JSONObject().apply {
                    put("title", text)
                    put("role", "user")
                    if (fileNames.length() > 0) put("files", fileNames)
                }
                repo.dao.put(AcademicRecord(user, kind = "CHAT", payload = userPayload.toString()))
                agentStage.value = if (extract) "Preparing a review proposal" else "Reading academic context"
                val allAttachments = files + uris.map { it.toString() }
                val response = provider.run(ApiKeyManager.getApiKey(getApplication()) ?: error("API key unavailable"), modelToUse, request, if (extract) "extract" else "answer", context.groupId, context.messageIds, allAttachments)
                ensureActive();check(owner.value==user){"Account changed; result discarded"}
                val proposal=response.optJSONObject("proposal")
                if(proposal!=null){repo.cacheProposal(proposal,context.groupId);agentAnswer.value=proposal.optString("answer");agentStage.value="Awaiting review in Inbox"}
                else {agentAnswer.value=response.optString("answer");agentStage.value="Answer ready"}
                repo.dao.put(AcademicRecord(user,kind="CHAT",payload=JSONObject().put("title",agentAnswer.value).put("role","assistant").toString()))
            }catch(e:CancellationException){agentStage.value="Cancelled — no academic changes applied";throw e}
            catch(e:Exception){
                Log.e("WorkspaceViewModel", "runAgent error", e)
                agentStage.value="Failed — no academic changes applied"
                message.value=safeError(e)
            }
        }
    }
    fun cancelAgent(){agentJob?.cancel()}
    fun importAttendance(parsed: AumsScraper.ParsedAumsData, targetSemester: Int = semester.value) = action {
        require(parsed.attendanceList.isNotEmpty()) { "No valid attendance rows found; existing records were preserved" }
        semester.value = targetSemester
        for (row in parsed.attendanceList) {
            val course = records.value.find { it.kind == "COURSE" && it.groupId.isBlank() && it.json().optString("code") == row.subjectCode && it.json().optInt("semester") == targetSemester }
            var cid = course?.id
            if (cid == null) {
                val data = JSONObject().put("title", row.subjectName.ifBlank { row.subjectCode }).put("code", row.subjectCode).put("semester", targetSemester)
                repo.saveManual("COURSE", data)
                cid = repo.dao.all(owner.value).first { it.kind == "COURSE" && it.json().optString("code") == row.subjectCode && it.json().optInt("semester") == targetSemester }.id
            }
            val state = repo.dao.get(owner.value, "personal:$cid")?.json() ?: JSONObject()
            repo.personal(cid, state.put("attended", row.attendedClasses).put("total", row.totalClasses).put("syncedAt", System.currentTimeMillis()))
        }
        message.value = "Imported ${parsed.attendanceList.size} courses for semester $targetSemester"
    }
}
