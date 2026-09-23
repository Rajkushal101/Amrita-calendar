package acn.amrita.chen.planner.ui.screens
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import acn.amrita.chen.planner.workspace.*
@Composable
fun AssistantScreen(viewModel: acn.amrita.chen.planner.ui.MainViewModel) {
    val workspace: WorkspaceViewModel = viewModel()
    AgentSheet(workspace, AgentContext()) { }
}
