package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.StudentProfileInput
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CoralRose
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StudentProfileDialog(
    initialName: String,
    initialCourse: String,
    initialSchool: String,
    isSaving: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSave: (StudentProfileInput) -> Unit
) {
    var studentName by remember(initialName) { mutableStateOf(initialName) }
    var course by remember(initialCourse) { mutableStateOf(initialCourse) }
    var school by remember(initialSchool) { mutableStateOf(initialSchool) }
    var showValidationErrors by remember { mutableStateOf(false) }

    val nameValid = studentName.trim().length in 2..50
    val courseValid = course.trim().length in 1..30
    val schoolValid = school.trim().length in 2..80

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyanElectric,
        unfocusedBorderColor = BorderSubtle,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedContainerColor = SlateSurfaceVariant,
        unfocusedContainerColor = SlateSurfaceVariant,
        focusedLabelColor = CyanElectric,
        unfocusedLabelColor = TextSecondary
    )

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ObsidianSurface,
            border = BorderStroke(1.5.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("student_profile_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.student_profile_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.student_profile_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                OutlinedTextField(
                    value = studentName,
                    onValueChange = { if (it.length <= 50) studentName = it },
                    label = { Text(stringResource(R.string.label_student_name)) },
                    placeholder = { Text(stringResource(R.string.placeholder_student_name)) },
                    leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                    singleLine = true,
                    isError = showValidationErrors && !nameValid,
                    supportingText = if (showValidationErrors && !nameValid) {
                        { Text(stringResource(R.string.error_student_name_length)) }
                    } else null,
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_name_input")
                )

                OutlinedTextField(
                    value = course,
                    onValueChange = { if (it.length <= 30) course = it },
                    label = { Text(stringResource(R.string.label_student_course)) },
                    placeholder = { Text(stringResource(R.string.placeholder_student_course)) },
                    leadingIcon = { Icon(Icons.Rounded.Class, contentDescription = null) },
                    singleLine = true,
                    isError = showValidationErrors && !courseValid,
                    supportingText = if (showValidationErrors && !courseValid) {
                        { Text(stringResource(R.string.error_student_course_length)) }
                    } else null,
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_course_input")
                )

                OutlinedTextField(
                    value = school,
                    onValueChange = { if (it.length <= 80) school = it },
                    label = { Text(stringResource(R.string.label_student_school)) },
                    placeholder = { Text(stringResource(R.string.placeholder_student_school)) },
                    leadingIcon = { Icon(Icons.Rounded.School, contentDescription = null) },
                    singleLine = true,
                    isError = showValidationErrors && !schoolValid,
                    supportingText = if (showValidationErrors && !schoolValid) {
                        { Text(stringResource(R.string.error_student_school_length)) }
                    } else null,
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_school_input")
                )

                if (serverError != null) {
                    Text(
                        text = serverError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoralRose
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            if (nameValid && courseValid && schoolValid) {
                                onSave(
                                    StudentProfileInput(
                                        studentName = studentName,
                                        course = course,
                                        school = school
                                    )
                                )
                            } else {
                                showValidationErrors = true
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanElectric,
                            contentColor = MidnightBg
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("save_profile_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MidnightBg,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.action_save_profile))
                    }
                }
            }
        }
    }
}
