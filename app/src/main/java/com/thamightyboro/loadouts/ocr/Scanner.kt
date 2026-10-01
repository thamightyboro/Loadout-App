package com.thamightyboro.loadouts.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.thamightyboro.loadouts.data.Part
import kotlinx.coroutines.tasks.await

object Scanner {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Reads an image (camera photo or screenshot) and returns a draft part plus the raw text. */
    suspend fun scan(context: Context, uri: Uri): Pair<Part, String> {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        val lines = result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
        }
        return ExamineParser.parse(lines) to result.text
    }
}
