package acn.amrita.chen.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onAdd: (title: String, type: String, timeString: String?, notes: String?, reminderType: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var timeString by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("exam") }
    var reminderType by remember { mutableStateOf("none") }
    
    val types = listOf("exam", "personal", "meeting", "event", "deadline", "holiday")
    val reminders = listOf("none" to "No Reminder", "same" to "On the day (9 AM)", "1d" to "1 Day Before", "3d" to "3 Days Before", "1w" to "1 Week Before")
    
    var reminderExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Event / Reminder", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Event Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Type", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(types) { t ->
                        val (bg, textCol) = getTypeColors(t)
                        val isSelected = type == t
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) textCol else bg)
                                .clickable { type = t }
                                .padding(vertical = 6.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(text = t.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = if (isSelected) Color.White else textCol)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = timeString,
                    onValueChange = { timeString = it },
                    label = { Text("Time (e.g. 14:30)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("Reminder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                ExposedDropdownMenuBox(
                    expanded = reminderExpanded,
                    onExpandedChange = { reminderExpanded = !reminderExpanded }
                ) {
                    OutlinedTextField(
                        value = reminders.find { it.first == reminderType }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = reminderExpanded,
                        onDismissRequest = { reminderExpanded = false }
                    ) {
                        reminders.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    reminderType = key
                                    reminderExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank()) {
                    onAdd(title, type, timeString.takeIf { it.isNotBlank() }, notes.takeIf { it.isNotBlank() }, reminderType)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
