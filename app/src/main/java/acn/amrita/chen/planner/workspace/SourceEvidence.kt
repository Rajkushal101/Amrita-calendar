package acn.amrita.chen.planner.workspace

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SourceEvidence(vm:WorkspaceViewModel,group:String,sources:JSONArray) {
    val context=LocalContext.current
    val isDark = MaterialTheme.colorScheme.background == acn.amrita.chen.planner.ui.theme.AcnDarkBackground
    val textBtnColor = if (isDark) MaterialTheme.colorScheme.onSurface else acn.amrita.chen.planner.ui.theme.AcnBrandCrimson
    var opened by remember{mutableStateOf<Pair<JSONObject,JSONObject>?>(null)}
    for(i in 0 until sources.length()) {
        val source=sources.getJSONObject(i)
        TextButton(onClick={vm.action{opened=source to vm.repo.sourceDetail(group,source)}}, colors = ButtonDefaults.textButtonColors(contentColor = textBtnColor)){Text("View source: ${source.optString("name",source.optString("type"))}")}
    }
    opened?.let{(reference,data)->AlertDialog(onDismissRequest={opened=null},title={Text("Supporting source")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Text("${reference.optString("type")} · ${reference.optString("id")}",style=MaterialTheme.typography.labelSmall)
        if(reference.has("revision"))Text("Used revision ${reference.optLong("revision")} · current ${data.optLong("revision")}")
        Text(if(data.optBoolean("deleted"))"This message was removed. Published facts require review."else data.optString("text",data.optString("name")))
    }},confirmButton={TextButton(onClick={opened=null}){Text("Close")}},dismissButton={
        if(reference.optString("type")=="attachment")TextButton(onClick={vm.action{
            val file=vm.repo.openAttachment(data)
            val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri,data.optString("mime")).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }}){Text("Open file")}
    })}
}

@Composable
fun VersionHistory(vm:WorkspaceViewModel,record:AcademicRecord) {
    var revisions by remember(record.id){mutableStateOf<List<JSONObject>?>(null)}
    var restoring by remember{mutableStateOf<JSONObject?>(null)}
    val groups by vm.repo.groups.collectAsState()
    val canEdit=record.groupId.isBlank()||groups.find{it.optString("id")==record.groupId}?.optString("role") in listOf("owner","administrator","publisher")
    val isDark = MaterialTheme.colorScheme.background == acn.amrita.chen.planner.ui.theme.AcnDarkBackground
    val textBtnColor = if (isDark) MaterialTheme.colorScheme.onSurface else acn.amrita.chen.planner.ui.theme.AcnBrandCrimson
    TextButton(onClick={vm.action{revisions=vm.repo.history(record)}}, colors = ButtonDefaults.textButtonColors(contentColor = textBtnColor)){Text("Version history")}
    revisions?.let{rows->AlertDialog(onDismissRequest={revisions=null},title={Text("${record.title} · history")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        if(rows.isEmpty())Text("No earlier revisions are available.")
        rows.forEach{r->
            Text("Revision ${r.optLong("revision")} · ${r.optString("actor")}",style=MaterialTheme.typography.labelLarge)
            Text(r.optJSONObject("data")?.toString()?:"No details")
            if(canEdit&&r.optLong("revision")!=record.revision)TextButton(onClick={restoring=r}, colors = ButtonDefaults.textButtonColors(contentColor = textBtnColor)){Text("Review restoration")}
        }
    }},confirmButton={TextButton(onClick={revisions=null}){Text("Close")}})}
    restoring?.let{old->AlertDialog(onDismissRequest={restoring=null},title={Text("Restore as a new revision?")},text={Text("Replace revision ${record.revision} with the details from revision ${old.optLong("revision")}. History and private progress remain available. A newer server version will block this change.")},confirmButton={TextButton(onClick={vm.action{
        vm.repo.saveManual(record.kind,old.getJSONObject("data"),record.courseId,record,record.groupId)
        restoring=null;revisions=null;vm.message.value="Earlier details restored as a new revision"
    }}){Text("Apply restoration")}},dismissButton={TextButton(onClick={restoring=null}){Text("Cancel")}})}
}
