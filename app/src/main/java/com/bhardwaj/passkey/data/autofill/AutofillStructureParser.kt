package com.bhardwaj.passkey.data.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/**
 * The fields of one form that this app can fill or save.
 *
 * Only the traversal lives here. Which entries match, and which of their values are the username
 * and the password, is decided by AutofillMatcher, which has no Android types and is tested.
 */
data class AutofillFields(
    val usernameIds: List<AutofillId> = emptyList(),
    val passwordIds: List<AutofillId> = emptyList(),
    val webDomain: String? = null
) {
    /** A password field alone is fillable; a username field alone is not worth offering. */
    val isFillable: Boolean get() = passwordIds.isNotEmpty()

    fun allIds(): Array<AutofillId> = (usernameIds + passwordIds).toTypedArray()
}

object AutofillStructureParser {

    // Matched against view ids and HTML attribute names, which stay English even in a fully
    // localized app - unlike the user-visible labels the password analyser once matched on.
    private val PASSWORD_HINTS = listOf("password", "passwd", "pwd", "pass")
    private val USERNAME_HINTS = listOf("username", "user", "email", "login", "account", "phone")

    fun parse(structure: AssistStructure): AutofillFields {
        val usernames = mutableListOf<AutofillId>()
        val passwords = mutableListOf<AutofillId>()
        var domain: String? = null

        for (i in 0 until structure.windowNodeCount) {
            fun visit(node: AssistStructure.ViewNode) {
                node.webDomain?.takeIf { it.isNotBlank() }?.let { if (domain == null) domain = it }
                val id = node.autofillId
                if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
                    when (classify(node)) {
                        FieldKind.PASSWORD -> passwords += id
                        FieldKind.USERNAME -> usernames += id
                        FieldKind.UNKNOWN -> Unit
                    }
                }
                for (child in 0 until node.childCount) visit(node.getChildAt(child))
            }
            visit(structure.getWindowNodeAt(i).rootViewNode)
        }

        return AutofillFields(
            usernameIds = usernames,
            passwordIds = passwords,
            webDomain = domain
        )
    }

    /** What the user typed into a form, read back when the framework offers to save it. */
    data class SavedCredential(
        val username: String?,
        val password: String,
        val webDomain: String?
    )

    /**
     * Reads the values out of a submitted form.
     *
     * A save request arrives after the user has already typed, so the fields carry values; the
     * fill path deliberately ignores them.
     */
    fun extractCredential(structure: AssistStructure): SavedCredential? {
        var username: String? = null
        var password: String? = null
        var domain: String? = null

        for (i in 0 until structure.windowNodeCount) {
            fun visit(node: AssistStructure.ViewNode) {
                node.webDomain?.takeIf { it.isNotBlank() }?.let { if (domain == null) domain = it }
                val text = node.autofillValue
                    ?.takeIf { it.isText }
                    ?.textValue
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
                if (text != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
                    when (classify(node)) {
                        FieldKind.PASSWORD -> if (password == null) password = text
                        FieldKind.USERNAME -> if (username == null) username = text
                        FieldKind.UNKNOWN -> Unit
                    }
                }
                for (child in 0 until node.childCount) visit(node.getChildAt(child))
            }
            visit(structure.getWindowNodeAt(i).rootViewNode)
        }

        val secret = password ?: return null
        return SavedCredential(username = username, password = secret, webDomain = domain)
    }

    private enum class FieldKind { USERNAME, PASSWORD, UNKNOWN }

    /**
     * Declared hints first, then the keyboard type, then the view's own id. The order matters:
     * an app that declares its hints correctly should never be second-guessed by a substring.
     */
    private fun classify(node: AssistStructure.ViewNode): FieldKind {
        node.autofillHints?.forEach { hint ->
            when (hint) {
                View.AUTOFILL_HINT_PASSWORD -> return FieldKind.PASSWORD
                View.AUTOFILL_HINT_USERNAME,
                View.AUTOFILL_HINT_EMAIL_ADDRESS -> return FieldKind.USERNAME
            }
            // Compose and several libraries emit lowercase or vendor-prefixed hints.
            val normalized = hint.lowercase()
            if (PASSWORD_HINTS.any { normalized.contains(it) }) return FieldKind.PASSWORD
            if (USERNAME_HINTS.any { normalized.contains(it) }) return FieldKind.USERNAME
        }

        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val klass = node.inputType and InputType.TYPE_MASK_CLASS
        if (klass == InputType.TYPE_CLASS_TEXT) {
            when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return FieldKind.PASSWORD

                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> return FieldKind.USERNAME
            }
        }

        val text = listOfNotNull(
            node.idEntry,
            node.hint,
            node.htmlInfo?.attributes?.firstOrNull { it.first == "name" }?.second
        ).joinToString(" ").lowercase()
        if (text.isBlank()) return FieldKind.UNKNOWN
        // Password is checked first: "user_password" is a password field, not a username one.
        if (PASSWORD_HINTS.any { text.contains(it) }) return FieldKind.PASSWORD
        if (USERNAME_HINTS.any { text.contains(it) }) return FieldKind.USERNAME
        return FieldKind.UNKNOWN
    }
}
