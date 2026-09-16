package com.bhardwaj.passkey.presentation.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import com.bhardwaj.passkey.R
import com.bhardwaj.passkey.data.autofill.AutofillResponseBuilder
import com.bhardwaj.passkey.data.autofill.AutofillStructureParser
import com.bhardwaj.passkey.data.local.VaultDatabaseProvider
import com.bhardwaj.passkey.domain.model.Category
import com.bhardwaj.passkey.domain.repository.PasskeyRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fills credentials into other apps and web pages.
 *
 * The third onboarding screen has advertised "Don't Type, Autofill Your Credentials" since the
 * app shipped, and no AutofillService existed. This is that promise.
 *
 * The vault is encrypted and usually locked, so a fill request has two outcomes: if the vault is
 * open, real datasets; if not, one authentication entry that opens [AutofillUnlockActivity] and
 * comes back with the datasets. Nothing about the vault's contents - not even how many entries
 * match - is visible before the user authenticates.
 */
@AndroidEntryPoint
class PasskeyAutofillService : AutofillService() {

    @Inject
    lateinit var vault: VaultDatabaseProvider

    @Inject
    lateinit var responseBuilder: AutofillResponseBuilder

    @Inject
    lateinit var repository: PasskeyRepository

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val context = request.fillContexts.lastOrNull()
        if (context == null) {
            callback.onSuccess(null)
            return
        }
        val structure = context.structure
        val fields = AutofillStructureParser.parse(structure)
        if (!fields.isFillable) {
            callback.onSuccess(null)
            return
        }

        if (!vault.isOpen) {
            callback.onSuccess(
                responseBuilder.authenticationResponse(fields, authenticationSender())
            )
            return
        }

        val clientPackage = structure.activityComponent?.packageName
        val work = scope.launch {
            val credentials = responseBuilder.credentialsFor(fields, clientPackage)
            callback.onSuccess(responseBuilder.fillResponse(fields, credentials))
        }
        cancellationSignal.setOnCancelListener { work.cancel() }
    }

    /**
     * Saves a credential the user has just typed elsewhere.
     *
     * Only possible while the vault is open: writing needs the key, and there is no way to
     * authenticate from inside a save request. A locked vault reports failure rather than
     * silently dropping the credential, so the user is told nothing was saved.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val credential = structure?.let { AutofillStructureParser.extractCredential(it) }
        if (credential == null || credential.password.isBlank()) {
            callback.onFailure(getString(R.string.autofill_save_failed))
            return
        }
        if (!vault.isOpen) {
            callback.onFailure(getString(R.string.autofill_save_locked))
            return
        }

        val heading = credential.heading(structure.activityComponent?.packageName)
        scope.launch {
            val saved = runCatching {
                repository.runInTransaction {
                    val previewId = repository.getPreviewByHeading(heading, Category.OTHERS)?.id
                        ?: repository.createPreview(heading, Category.OTHERS)
                    credential.username?.takeIf { it.isNotBlank() }?.let {
                        repository.createDetail(previewId, USERNAME_LABEL, it, sequence = 0)
                    }
                    repository.createDetail(
                        previewId = previewId,
                        question = PASSWORD_LABEL,
                        answer = credential.password,
                        sequence = 1,
                        // Known to be a secret by where it came from, so the analyser never has
                        // to guess for it in any locale.
                        isSecret = true
                    )
                }
                true
            }.getOrDefault(false)

            if (saved) {
                callback.onSuccess()
            } else {
                callback.onFailure(getString(R.string.autofill_save_failed))
            }
        }
    }

    private fun authenticationSender(): android.content.IntentSender {
        // Mutable because the framework adds the assist structure to this intent before
        // launching it. Below API 31 there is no flag to pass: PendingIntents were mutable by
        // default, and FLAG_IMMUTABLE is what had to be asked for.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getActivity(
            this,
            AUTH_REQUEST_CODE,
            Intent(this, AutofillUnlockActivity::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT or mutable
        ).intentSender
    }

    private companion object {
        const val AUTH_REQUEST_CODE = 9001
        const val USERNAME_LABEL = "Username"
        const val PASSWORD_LABEL = "Password"
    }
}

private fun AutofillStructureParser.SavedCredential.heading(clientPackage: String?): String {
    // The web domain names the service; a package name is the fallback, tidied to its most
    // distinctive part so the entry reads "instagram" rather than "com.instagram.android".
    webDomain?.substringAfter("://")?.substringBefore('/')?.takeIf { it.isNotBlank() }
        ?.let { return it }
    val parts = clientPackage?.split('.').orEmpty()
    return parts.getOrNull(1) ?: clientPackage ?: "Autofill"
}
