package com.thamightyboro.loadouts

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thamightyboro.loadouts.data.Loadout
import com.thamightyboro.loadouts.data.Part
import com.thamightyboro.loadouts.data.Store
import com.thamightyboro.loadouts.export.LoadoutImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.thamightyboro.loadouts.ocr.Scanner
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val data = store.data

    var scanning by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /** A scanned-but-unsaved part waiting for the edit screen to pick it up. */
    private var draft: Part? = null
    var draftRawText: String? = null
        private set

    init {
        viewModelScope.launch { store.load() }
    }

    fun scan(uri: Uri, onDone: () -> Unit) {
        scanning = true
        viewModelScope.launch {
            runCatching { Scanner.scan(getApplication(), uri) }
                .onSuccess { (part, raw) ->
                    draft = part
                    draftRawText = raw
                    if (part.stats.isEmpty()) message = "Couldn't find any stats - check the photo or fill in by hand."
                    onDone()
                }
                .onFailure { message = "Scan failed: ${it.message}" }
            scanning = false
        }
    }

    fun takeDraft(): Part? = draft.also { draft = null }
    fun clearRaw() { draftRawText = null }

    fun savePart(p: Part) = viewModelScope.launch { store.upsertPart(p) }
    fun deletePart(id: String) = viewModelScope.launch { store.deletePart(id) }
    fun saveLoadout(l: Loadout) = viewModelScope.launch { store.upsertLoadout(l) }
    fun deleteLoadout(id: String) = viewModelScope.launch { store.deleteLoadout(id) }

    var exporting by mutableStateOf(false)
        private set

    /** Draws the loadout as a 1920x1080 image, saves it to the gallery and opens the share sheet. */
    fun exportImage(loadout: Loadout, parts: Map<String, Part>) {
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { LoadoutImage.save(getApplication(), loadout, parts) }
            }.onSuccess { saved ->
                if (saved.inGallery) message = "Saved to Pictures/SWG Loadouts"
                LoadoutImage.share(getApplication(), saved.shareUri)
            }.onFailure { message = "Image export failed: ${it.message}" }
            exporting = false
        }
    }

    fun exportJson() = store.exportJson()
    fun importJson(text: String) = viewModelScope.launch {
        runCatching { store.importJson(text) }
            .onSuccess { message = "Backup imported." }
            .onFailure { message = "That file isn't a valid backup." }
    }
}
