package com.rfid.rolltrack.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rfid.rolltrack.AppContainer
import com.rfid.rolltrack.data.ScanRepository
import com.rfid.rolltrack.data.SubmitOp
import com.rfid.rolltrack.data.SubmitSummary
import com.rfid.rolltrack.data.local.ItemState
import com.rfid.rolltrack.data.local.ScanItemEntity
import com.rfid.rolltrack.data.local.SessionType
import com.rfid.rolltrack.scanner.ScanEvent
import com.rfid.rolltrack.ui.components.Banner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

abstract class ScanSessionViewModel<H>(
    val container: AppContainer,
    val type: SessionType,
    private val headerSerializer: KSerializer<H>,
    private val emptyHeader: () -> H
) : ViewModel() {

    protected val repo: ScanRepository get() = container.scanRepository
    private val json = Json { ignoreUnknownKeys = true }

    protected val _sessionId = MutableStateFlow<Long?>(null)
    val sessionId: StateFlow<Long?> = _sessionId.asStateFlow()

    protected val _header = MutableStateFlow<H?>(null)
    val header: StateFlow<H?> = _header.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<ScanItemEntity>> = _sessionId.flatMapLatest { id ->
        if (id != null) repo.observeItems(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    protected val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    protected val _summary = MutableStateFlow<SubmitSummary?>(null)
    val summary: StateFlow<SubmitSummary?> = _summary.asStateFlow()

    protected val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val sendable: List<ScanItemEntity>
        get() = items.value.filter { it.state == ItemState.LOCAL || it.state == ItemState.FAILED }

    init {
        viewModelScope.launch {
            val userId = container.settings.session.value.userId.ifBlank { "1" }
            val session = repo.openSession(type, userId)
            _sessionId.value = session.id
            if (!session.headerJson.isNullOrBlank()) {
                try {
                    _header.value = json.decodeFromString(headerSerializer, session.headerJson)
                } catch (_: Exception) {}
            }
            onSessionRestored(session.id)
        }
    }

    open suspend fun onSessionRestored(id: Long) {}

    fun onScan(event: ScanEvent) {
        com.rfid.rolltrack.data.remote.ApiLog.info(
            "scan -> $type: " + when (event) {
                is ScanEvent.Tag -> "ROLL TAG ${event.tagId}"
                is ScanEvent.Code -> "CODE (not a tag) '${event.raw}'"
            } + " session=${_sessionId.value}"
        )
        viewModelScope.launch {
            when (event) {
                is ScanEvent.Tag -> onTag(event)
                is ScanEvent.Code -> onCode(event)
            }
        }
    }

    open suspend fun onTag(event: ScanEvent.Tag) {
        addTag(event)
    }

    open suspend fun onCode(event: ScanEvent.Code) {
        show(Banner.Tone.WARN, "Unexpected code: ${event.raw}")
    }

    suspend fun addTag(event: ScanEvent.Tag, weightKg: Double? = null): Boolean {
        val currentItems = items.value
        if (currentItems.any { it.tagId == event.tagId }) {
            container.feedback.duplicate()
            show(Banner.Tone.INFO, "Already scanned: ${event.tagId}")
            return false
        }

        val sId = _sessionId.value ?: return false
        val entity = ScanItemEntity(
            sessionId = sId,
            tagId = event.tagId,
            source = event.source.label,
            weightKg = weightKg
        )
        repo.addItem(entity)
        container.feedback.tagAccepted()
        onTagAdded(event.tagId)
        return true
    }

    open suspend fun onTagAdded(tagId: String) {}

    fun removeItem(item: ScanItemEntity) {
        viewModelScope.launch {
            repo.removeItem(item.id)
        }
    }

    fun saveHeader(header: H) {
        _header.value = header
        val sId = _sessionId.value ?: return
        viewModelScope.launch {
            val encoded = json.encodeToString(headerSerializer, header)
            repo.saveHeader(sId, encoded)
        }
    }

    fun submit(op: SubmitOp) {
        val sId = _sessionId.value ?: return
        val currentSendable = sendable
        _busy.value = true
        viewModelScope.launch {
            try {
                val res = repo.submit(sId, op, currentSendable.map { it.id })
                _summary.value = res
                afterSubmit(res)
            } catch (e: Exception) {
                show(Banner.Tone.BAD, e.message ?: "Submission failed")
            } finally {
                _busy.value = false
            }
        }
    }

    open suspend fun afterSubmit(result: SubmitSummary) {}

    fun show(tone: Banner.Tone, message: String) {
        _banner.value = Banner(tone, message)
    }

    fun dismissBanner() {
        _banner.value = null
    }

    fun dismissSummary() {
        _summary.value = null
    }

    fun startNew() {
        val sId = _sessionId.value ?: return
        viewModelScope.launch {
            repo.closeSession(sId)
            val userId = container.settings.session.value.userId.ifBlank { "1" }
            val newSession = repo.openSession(type, userId)
            _sessionId.value = newSession.id
            _header.value = emptyHeader()
            _summary.value = null
            _banner.value = null
        }
    }
}
