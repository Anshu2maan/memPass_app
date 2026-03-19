package com.example.mempass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RichTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    var textFieldValue by remember { 
        mutableStateOf(TextFieldValue(value, TextRange(value.length))) 
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    Column(modifier = modifier) {
        // Toolbar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorAction(Icons.Default.FormatBold, "Bold") {
                    textFieldValue = wrapSelection(textFieldValue, "**")
                    onValueChange(textFieldValue.text)
                }
                EditorAction(Icons.Default.FormatItalic, "Italic") {
                    textFieldValue = wrapSelection(textFieldValue, "_")
                    onValueChange(textFieldValue.text)
                }
                EditorAction(Icons.Default.FormatUnderlined, "Underline") {
                    textFieldValue = wrapSelection(textFieldValue, "<u>", "</u>")
                    onValueChange(textFieldValue.text)
                }
                EditorAction(Icons.Default.FormatListBulleted, "List") {
                    textFieldValue = insertAtLineStart(textFieldValue, "• ")
                    onValueChange(textFieldValue.text)
                }
                EditorAction(Icons.Default.Code, "Code") {
                    textFieldValue = wrapSelection(textFieldValue, "`")
                    onValueChange(textFieldValue.text)
                }
            }
        }

        TextField(
            value = textFieldValue,
            onValueChange = { 
                textFieldValue = it
                onValueChange(it.text)
            },
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 300.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                lineHeight = (17 * 1.5).sp
            )
        )
    }
}

@Composable
private fun EditorAction(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Icon(icon, label, modifier = Modifier.size(20.dp), tint = tint)
    }
}

private fun wrapSelection(value: TextFieldValue, tag: String): TextFieldValue {
    return wrapSelection(value, tag, tag)
}

private fun wrapSelection(value: TextFieldValue, startTag: String, endTag: String): TextFieldValue {
    val selection = value.selection
    val text = value.text
    
    val selectedText = text.substring(selection.start, selection.end)
    val newText = text.substring(0, selection.start) + startTag + selectedText + endTag + text.substring(selection.end)
    
    val newSelection = TextRange(
        selection.start + startTag.length,
        selection.end + startTag.length
    )
    
    return TextFieldValue(newText, newSelection)
}

private fun insertAtLineStart(value: TextFieldValue, prefix: String): TextFieldValue {
    val selection = value.selection
    val text = value.text
    
    // Find the start of the current line
    var lineStart = selection.start
    while (lineStart > 0 && text[lineStart - 1] != '\n') {
        lineStart--
    }
    
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    val newSelection = TextRange(selection.start + prefix.length, selection.end + prefix.length)
    
    return TextFieldValue(newText, newSelection)
}
