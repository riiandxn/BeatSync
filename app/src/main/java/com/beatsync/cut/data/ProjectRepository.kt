package com.beatsync.cut.data

import android.content.Context
import android.net.Uri
import com.beatsync.cut.model.MediaBinItem
import com.beatsync.cut.model.ProjectDraft
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

class ProjectRepository(private val context: Context) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val projectsDir = File(context.filesDir, "beatsync_projects").apply { mkdirs() }
    private val indexFile = File(projectsDir, "drafts_index.json")

    private val _drafts = MutableStateFlow<List<ProjectDraft>>(emptyList())
    val drafts: StateFlow<List<ProjectDraft>> = _drafts.asStateFlow()

    init {
        reloadAll()
    }

    fun reloadAll() {
        val loaded = if (indexFile.exists()) {
            try {
                val type = object : TypeToken<List<ProjectDraft>>() {}.type
                gson.fromJson<List<ProjectDraft>>(indexFile.readText(), type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        val validated = loaded.map { validateMediaAvailability(it) }
            .sortedByDescending { it.updatedAtMs }
        _drafts.value = validated
    }

    fun saveProject(draft: ProjectDraft) {
        val updated = draft.copy(updatedAtMs = System.currentTimeMillis())
        val currentList = _drafts.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            currentList[idx] = updated
        } else {
            currentList.add(0, updated)
        }
        persistList(currentList)
    }

    fun duplicateProject(source: ProjectDraft): ProjectDraft {
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            title = "${source.title} (Copy)",
            updatedAtMs = System.currentTimeMillis(),
            slots = source.slots.map { it.copy(id = UUID.randomUUID().toString()) }
        )
        val currentList = _drafts.value.toMutableList()
        currentList.add(0, copy)
        persistList(currentList)
        return copy
    }

    fun deleteProject(projectId: String) {
        val updated = _drafts.value.filterNot { it.id == projectId }
        persistList(updated)
    }

    fun relinkMediaItem(projectId: String, mediaBinId: String, newUri: Uri, newName: String, newDurationMs: Long): ProjectDraft? {
        val target = _drafts.value.find { it.id == projectId } ?: return null
        val updatedBin = target.mediaBin.map { item ->
            if (item.id == mediaBinId) {
                item.copy(
                    uriString = newUri.toString(),
                    displayName = newName,
                    totalDurationMs = newDurationMs,
                    isMissing = false
                )
            } else item
        }
        val updatedProject = target.copy(mediaBin = updatedBin, updatedAtMs = System.currentTimeMillis())
        saveProject(updatedProject)
        return updatedProject
    }

    private fun validateMediaAvailability(draft: ProjectDraft): ProjectDraft {
        val checkedBin = draft.mediaBin.map { item ->
            val exists = isUriAccessible(item.uriString)
            item.copy(isMissing = !exists)
        }
        return draft.copy(mediaBin = checkedBin)
    }

    private fun isUriAccessible(uriString: String): Boolean {
        if (uriString.isBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                File(uri.path ?: "").exists()
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun persistList(list: List<ProjectDraft>) {
        val sorted = list.sortedByDescending { it.updatedAtMs }
        _drafts.value = sorted
        try {
            val tmpFile = File(projectsDir, "drafts_index.tmp")
            tmpFile.writeText(gson.toJson(sorted))
            tmpFile.renameTo(indexFile)
        } catch (_: Exception) {
        }
    }
}
