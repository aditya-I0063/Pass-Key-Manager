package com.bhardwaj.passkey.presentation.screens.detail_screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.domain.model.Detail
import com.bhardwaj.passkey.domain.model.PasswordPolicy
import com.bhardwaj.passkey.domain.model.TotpEntry
import com.bhardwaj.passkey.domain.totp.Base32
import com.bhardwaj.passkey.domain.totp.OtpAuthUri
import com.bhardwaj.passkey.domain.totp.TotpConfig
import com.bhardwaj.passkey.domain.repository.PasskeyRepository
import com.bhardwaj.passkey.presentation.navigation.NavRoute
import com.bhardwaj.passkey.utils.PasswordGenerator
import com.bhardwaj.passkey.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: PasskeyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _effects = Channel<DetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Decoded from the typed route, so it cannot be a -1 sentinel. */
    val previewId: Long = savedStateHandle.toRoute<NavRoute.Details>().previewId

    private val query = MutableStateFlow("")

    /**
     * Editor, pending delete and generator settings.
     *
     * Deliberately not in SavedStateHandle: it holds the in-progress answer, and SavedStateHandle
     * is serialized into the saved-instance-state bundle, which the system persists to disk.
     * Losing a half-typed entry to process death is the right side of that trade.
     */
    private val transient = MutableStateFlow(TransientState())

    private data class TransientState(
        val editor: DetailState.Editor? = null,
        val pendingDelete: Detail? = null,
        val policy: PasswordPolicy = PasswordPolicy(),
        val isPolicySheetOpen: Boolean = false,
        val totpEditor: DetailState.TotpEditor? = null,
        val history: DetailState.History? = null
    )

    val state: StateFlow<DetailState> = combine(
        query,
        transient,
        repository.getDetailsByPreviewId(previewId),
        repository.getTotpByPreviewId(previewId)
    ) { currentQuery, ui, items, authenticators ->
        DetailState(
            query = currentQuery,
            items = items
                .filter {
                    currentQuery.isBlank() ||
                        it.question.contains(currentQuery, true) ||
                        it.answer.contains(currentQuery, true)
                }
                .filterNot { it.id == ui.pendingDelete?.id },
            isLoading = false,
            editor = ui.editor,
            pendingDelete = ui.pendingDelete,
            policy = ui.policy,
            isPolicySheetOpen = ui.isPolicySheetOpen,
            authenticators = authenticators,
            totpEditor = ui.totpEditor,
            history = ui.history
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    fun onIntent(intent: DetailIntent) {
        when (intent) {
            is DetailIntent.QueryChanged -> query.value = intent.query

            DetailIntent.AddClicked -> {
                query.value = ""
                transient.update { it.copy(editor = DetailState.Editor()) }
            }

            is DetailIntent.EditClicked -> {
                query.value = ""
                transient.update {
                    it.copy(
                        editor = DetailState.Editor(
                            question = intent.detail.question,
                            answer = intent.detail.answer,
                            editingId = intent.detail.id
                        )
                    )
                }
            }

            is DetailIntent.QuestionChanged ->
                transient.update { it.copy(editor = it.editor?.copy(question = intent.question)) }

            is DetailIntent.AnswerChanged ->
                transient.update {
                    // Typing over a generated value means it is no longer generator output.
                    it.copy(editor = it.editor?.copy(answer = intent.answer, wasGenerated = false))
                }

            DetailIntent.EditorDismissed -> transient.update { it.copy(editor = null) }

            DetailIntent.SaveClicked -> save()

            is DetailIntent.SwipedToDelete ->
                // Nothing written yet; the old code deleted here and re-inserted on cancel, so a
                // process death while the confirmation was open lost the entry.
                transient.update { it.copy(pendingDelete = intent.detail) }

            DetailIntent.DeleteCancelled -> transient.update { it.copy(pendingDelete = null) }

            DetailIntent.DeleteConfirmed -> {
                val target = transient.value.pendingDelete ?: return
                viewModelScope.launch {
                    repository.deleteDetail(target)
                    transient.update { it.copy(pendingDelete = null) }
                }
            }

            is DetailIntent.Moved -> move(intent.fromIndex, intent.toIndex)

            is DetailIntent.LongPressed ->
                // Answers are secrets: marked sensitive so Android 13+ redacts the paste
                // preview, and auto-cleared from the clipboard.
                emit(DetailEffect.CopyToClipboard(intent.value, isSensitive = true))

            DetailIntent.BackClicked -> emit(DetailEffect.PopBackStack)

            DetailIntent.PolicyClicked -> transient.update { it.copy(isPolicySheetOpen = true) }

            DetailIntent.PolicyDismissed -> transient.update { it.copy(isPolicySheetOpen = false) }

            is DetailIntent.LengthChanged -> transient.update {
                it.copy(
                    policy = it.policy.copy(
                        length = intent.length
                            .coerceIn(PasswordPolicy.MIN_LENGTH, PasswordPolicy.MAX_LENGTH)
                    )
                )
            }

            is DetailIntent.CharacterClassToggled -> transient.update {
                it.copy(policy = it.policy.toggle(intent.characterClass, intent.enabled))
            }

            DetailIntent.AddAuthenticatorClicked ->
                transient.update { it.copy(totpEditor = DetailState.TotpEditor()) }

            is DetailIntent.AuthenticatorInputChanged -> transient.update {
                it.copy(totpEditor = it.totpEditor?.copy(input = intent.input, isInvalid = false))
            }

            DetailIntent.AuthenticatorEditorDismissed ->
                transient.update { it.copy(totpEditor = null) }

            DetailIntent.AuthenticatorSaveClicked -> addAuthenticator()

            is DetailIntent.AuthenticatorDeleteClicked -> viewModelScope.launch {
                repository.deleteTotp(intent.entry)
            }

            is DetailIntent.AuthenticatorCodeCopied ->
                emit(DetailEffect.CopyToClipboard(intent.code, isSensitive = true))

            is DetailIntent.HistoryClicked -> viewModelScope.launch {
                val entries = repository.getHistoryForDetail(intent.detail.id).first()
                transient.update {
                    it.copy(history = DetailState.History(intent.detail, entries))
                }
            }

            DetailIntent.HistoryDismissed -> transient.update { it.copy(history = null) }

            DetailIntent.GenerateClicked -> transient.update {
                val policy = it.policy
                val generated = PasswordGenerator.generate(
                    length = policy.length,
                    includeUpper = policy.includeUppercase,
                    includeLower = policy.includeLowercase,
                    includeNumbers = policy.includeDigits,
                    includeSpecial = policy.includeSymbols
                )
                it.copy(
                    editor = (it.editor ?: DetailState.Editor())
                        .copy(answer = generated, wasGenerated = true)
                )
            }
        }
    }

    /**
     * Accepts either the `otpauth://` link behind a QR code or the Base32 secret printed beside
     * it, because those are the two things a setup page actually offers.
     */
    private fun addAuthenticator() {
        val input = transient.value.totpEditor?.input?.trim().orEmpty()
        val config = OtpAuthUri.parse(input)
            ?: Base32.decode(input)?.let { TotpConfig(secret = it) }

        if (config == null) {
            transient.update { it.copy(totpEditor = it.totpEditor?.copy(isInvalid = true)) }
            return
        }

        viewModelScope.launch {
            val label = config.issuer?.takeIf { it.isNotBlank() }
                ?: config.account?.takeIf { it.isNotBlank() }
                ?: DEFAULT_AUTHENTICATOR_LABEL
            repository.createTotp(previewId = previewId, label = label, config = config)
            transient.update { it.copy(totpEditor = null) }
        }
    }

    private fun save() {
        val editor = transient.value.editor ?: return
        val question = editor.question.trim()
        // Trimmed on both paths. Previously only the create path trimmed, so a trailing space
        // crept into stored passwords on edit and failed silently wherever it was pasted.
        val answer = editor.answer.trim()

        if (question.isBlank() || answer.isBlank()) {
            transient.update { it.copy(editor = null) }
            emit(
                DetailEffect.ShowSnackbar(
                    UiText.StringResource(R.string.enter_valid_title_n_response)
                )
            )
            return
        }

        viewModelScope.launch {
            val editingId = editor.editingId
            if (editingId != null) {
                state.value.items.firstOrNull { it.id == editingId }?.let { existing ->
                    repository.updateDetail(
                        existing.copy(
                            question = question,
                            answer = answer,
                            isSecret = existing.isSecret || editor.wasGenerated
                        )
                    )
                }
            } else {
                repository.createDetail(
                    previewId = previewId,
                    question = question,
                    answer = answer,
                    isSecret = editor.wasGenerated
                )
            }
            transient.update { it.copy(editor = null) }
        }
    }

    private fun move(fromIndex: Int, toIndex: Int) {
        val current = state.value.items
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val reordered = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        viewModelScope.launch {
            // One transaction, not N separate writes.
            repository.runInTransaction {
                reordered.forEachIndexed { index, detail ->
                    repository.updateDetailSequence(detail.id, index.toLong())
                }
            }
        }
    }

    private fun emit(effect: DetailEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        /** Only reached by a hand-typed secret, which carries no issuer or account. */
        const val DEFAULT_AUTHENTICATOR_LABEL = "Authenticator"
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
