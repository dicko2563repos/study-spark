package com.studyspark.app.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studyspark.app.data.entity.CourseEntity

@Composable
fun CoursesScreen(
    courses: List<CourseEntity>,
    onAdd: (title: String, module: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var module by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Courses", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Track modules manually for now. Provider fields are ready for future Coursera/Udemy-style sync.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Course title") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = module,
            onValueChange = { module = it },
            label = { Text("First module (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (title.isNotBlank()) {
                    onAdd(title.trim(), module.trim())
                    title = ""
                    module = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add course") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(courses, key = { it.id }) { course ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(course.title, style = MaterialTheme.typography.titleLarge)
                        Text(course.notes.ifBlank { "Manual course" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
