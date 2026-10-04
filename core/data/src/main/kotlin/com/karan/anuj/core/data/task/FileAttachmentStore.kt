package com.karan.anuj.core.data.task

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.karan.anuj.core.data.di.IoDispatcher
import com.karan.anuj.core.domain.task.AttachmentFileStore
import com.karan.anuj.core.domain.task.IdGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Where photo files live, exposed so backup can copy the folder as a whole. */
class AttachmentDirectory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Created on first use. Inside the app's private storage, so no other app can read it. */
    val folder: File get() = File(context.filesDir, FOLDER).apply { mkdirs() }

    companion object {
        /** Must match the path in `res/xml/attachment_paths.xml`. */
        const val FOLDER = "attachments"
    }
}

@Singleton
class FileAttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directory: AttachmentDirectory,
    private val ids: IdGenerator,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AttachmentFileStore {

    override suspend fun importFrom(sourceUri: String): String = withContext(io) {
        val target = newFile()
        val input = context.contentResolver.openInputStream(Uri.parse(sourceUri))
            ?: error("Could not open the picked picture")
        try {
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
        } catch (failure: Exception) {
            /** A half-copied file would show as a broken photo, so it is not kept. */
            target.delete()
            throw failure
        }
        target.name
    }

    override suspend fun reserveForCamera(): String = withContext(io) {
        newFile().apply { createNewFile() }.name
    }

    override fun shareableUriFor(fileName: String): String =
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", fileOf(fileName)).toString()

    override fun pathOf(fileName: String): String = fileOf(fileName).absolutePath

    override suspend fun delete(fileNames: List<String>) = withContext(io) {
        fileNames.forEach { fileOf(it).delete() }
    }

    private fun newFile() = File(directory.folder, "${ids.newId()}.jpg")

    /** Only the last path segment is used, so a stored name can never point outside the attachment folder. */
    private fun fileOf(fileName: String) = File(directory.folder, File(fileName).name)

    private companion object {
        /** Must match the provider authority in this module's AndroidManifest.xml. */
        const val AUTHORITY_SUFFIX = ".files"
    }
}
