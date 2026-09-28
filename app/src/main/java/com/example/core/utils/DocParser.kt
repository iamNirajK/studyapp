package com.example.core.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.StringBuilder

object DocParser {

    private const val TAG = "DocParser"

    fun extractTextFromUri(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        
        return try {
            if (mimeType.contains("pdf", ignoreCase = true) || uri.path?.endsWith(".pdf", ignoreCase = true) == true) {
                // Parse PDF text from raw stream securely
                parsePdfRawText(context, uri)
            } else {
                // Standard text parsing
                parsePlainText(context, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document: ${e.message}", e)
            "Error parsing document: ${e.message}"
        }
    }

    private fun parsePlainText(context: Context, uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                var linesCount = 0
                while (line != null && linesCount < 1000) { // Limit to 1000 lines to optimize memory
                    stringBuilder.append(line).append("\n")
                    line = reader.readLine()
                    linesCount++
                }
            }
        }
        return stringBuilder.toString()
    }

    private fun parsePdfRawText(context: Context, uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, "ISO-8859-1")).use { reader ->
                var line: String?
                var charactersCount = 0
                val textBlockRegex = Regex("\\(([^)]+)\\)\\s*(Tj|TJ)")
                
                while (reader.readLine().also { line = it } != null && charactersCount < 80000) {
                    val lineStr = line ?: continue
                    
                    // PDFs store text in (Content) Tj or (Content) TJ format
                    val matches = textBlockRegex.findAll(lineStr)
                    for (match in matches) {
                        val text = match.groups[1]?.value ?: continue
                        // Skip PDF internal instructions and fonts
                        if (text.length > 1 && !text.startsWith("/") && !text.contains("Font")) {
                            // Unescape basic PDF escape sequences
                            val cleanText = text
                                .replace("\\(", "(")
                                .replace("\\)", ")")
                                .replace("\\\\", "\\")
                                .replace("\\r", " ")
                                .replace("\\n", " ")
                            
                            stringBuilder.append(cleanText)
                            charactersCount += cleanText.length
                        }
                    }
                    
                    // Also scan for simple text-like objects or stream sections
                    if (lineStr.contains("BT") && lineStr.contains("ET")) {
                        stringBuilder.append(" ")
                    }
                }
            }
        }
        
        val parsed = stringBuilder.toString().trim()
        return if (parsed.length > 50) {
            parsed
        } else {
            // Fallback: If no structured text could be extracted, return metadata about the file
            "Attached PDF document. Name/Uri: ${uri.lastPathSegment}. Please answer questions by guiding user on how to analyze this."
        }
    }
}
