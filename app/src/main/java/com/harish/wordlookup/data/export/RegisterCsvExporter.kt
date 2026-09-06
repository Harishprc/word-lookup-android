package com.harish.wordlookup.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.harish.wordlookup.data.RegisterEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Round 10: the app's first file-write of any kind. API 29+ only
 * (`MediaStore.Downloads`, zero permissions under scoped storage) - a
 * pre-29 fallback would need a runtime `WRITE_EXTERNAL_STORAGE` grant and a
 * second write path this repo's toolchain (android-35 emulator/device only)
 * can't verify, so `Failure` is returned outright below that API level
 * rather than attempting a legacy path.
 */
object RegisterCsvExporter {

    sealed interface Result {
        data class Success(val displayName: String) : Result
        data class Failure(val reason: String) : Result
    }

    suspend fun export(context: Context, entries: List<RegisterEntry>): Result = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext Result.Failure("Requires Android 10 or newer.")
        }

        val fileName = "word-lookup-export-${SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())}.csv"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext Result.Failure("Could not create the file.")

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(buildCsv(entries).toByteArray(Charsets.UTF_8)) }
                ?: error("no output stream")
        }
        if (written.isFailure) {
            return@withContext Result.Failure("Could not write the file.")
        }
        Result.Success(fileName)
    }

    private fun buildCsv(entries: List<RegisterEntry>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val header = listOf(
            "word", "part of speech", "meaning", "english synonyms", "english example",
            "translation", "native synonyms", "native example", "language", "date added",
        )
        val rows = entries.map { entry ->
            val r = entry.result
            listOf(
                r.original, r.partOfSpeech, r.meaning, r.synonyms, r.exampleEn,
                r.translation, r.synonymsNative, r.exampleNative, entry.language,
                dateFormat.format(Date(entry.createdAtMillis)),
            )
        }
        return (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { csvEscape(it) } } + "\r\n"
    }

    /** RFC 4180: quote a field if it contains a comma, quote, or newline; double up embedded quotes. */
    private fun csvEscape(field: String): String {
        val needsQuoting = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"${field.replace("\"", "\"\"")}\"" else field
    }
}
