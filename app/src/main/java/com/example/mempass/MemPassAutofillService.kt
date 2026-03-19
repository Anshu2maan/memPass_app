package com.example.mempass

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.example.mempass.common.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MemPassAutofillService : AutofillService() {
    private val TAG = "MemPassAutofill"
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Inject
    lateinit var repository: VaultRepository

    @Inject
    lateinit var vaultManager: VaultManager

    companion object {
        fun calculateMatchScore(entry: PasswordEntry, packageName: String?, webDomain: String?): Float {
            var score = 0f
            if (webDomain != null && entry.associatedDomain?.contains(webDomain) == true) {
                score += 0.8f
            }
            if (packageName != null && entry.associatedPackageName?.contains(packageName) == true) {
                score += 0.7f
            }
            return score
        }
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val fillContext = request.fillContexts.last()
        val structure = fillContext.structure
        val autofillIds = mutableListOf<AutofillId>()
        var webDomain: String? = null

        findAutofillIdsAndDomain(structure.getWindowNodeAt(0).rootViewNode, autofillIds) { domain ->
            webDomain = domain
        }

        if (autofillIds.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val serviceContext = this
        val currentPackageName = packageName

        serviceScope.launch {
            try {
                val appPackageName = structure.activityComponent?.packageName
                val allPasswords = repository.allPasswords.first()
                
                val matches = allPasswords.filter { entry ->
                    calculateMatchScore(entry, appPackageName, webDomain) >= 0.6f
                }

                if (matches.isEmpty()) {
                    callback.onSuccess(null)
                    return@launch
                }

                // If vault is locked, we need authentication
                if (!vaultManager.isUnlocked()) {
                    val authIntent = Intent(serviceContext, AutofillAuthActivity::class.java).apply {
                        putExtra(android.view.autofill.AutofillManager.EXTRA_ASSIST_STRUCTURE, structure)
                    }
                    val presentation = RemoteViews(currentPackageName, R.layout.autofill_inline_suggestion).apply {
                        setTextViewText(R.id.autofill_service, getString(R.string.unlock_mempass))
                        setTextViewText(R.id.autofill_username, getString(R.string.tap_to_authenticate))
                    }
                    
                    val response = FillResponse.Builder()
                        .setAuthentication(autofillIds.toTypedArray(), PendingIntent.getActivity(
                            serviceContext, 0, authIntent, 
                            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        ).intentSender, presentation)
                        .build()
                    callback.onSuccess(response)
                    return@launch
                }

                val responseBuilder = FillResponse.Builder()
                val key = vaultManager.getKey()!!

                matches.forEach { entry ->
                    var usernameChars: CharArray? = null
                    var passwordChars: CharArray? = null
                    try {
                        usernameChars = CryptoUtils.decryptToChars(entry.encryptedUsername, key)
                        passwordChars = CryptoUtils.decryptToChars(entry.encryptedPassword, key)

                        val username = String(usernameChars)
                        val presentation = RemoteViews(currentPackageName, R.layout.autofill_inline_suggestion).apply {
                            setTextViewText(R.id.autofill_service, entry.serviceName)
                            setTextViewText(R.id.autofill_username, username)
                        }

                        val datasetBuilder = Dataset.Builder(presentation)
                        
                        // Map fields in structure to decrypted values
                        val fieldMap = mutableMapOf<AutofillId, String>()
                        mapFields(structure.getWindowNodeAt(0).rootViewNode, username, String(passwordChars), fieldMap)
                        
                        fieldMap.forEach { (id, value) ->
                            datasetBuilder.setValue(id, AutofillValue.forText(value))
                        }

                        responseBuilder.addDataset(datasetBuilder.build())
                    } catch (e: Exception) {
                        Log.e(TAG, "Decryption for entry ${entry.serviceName} failed", e)
                    } finally {
                        usernameChars?.let { Arrays.fill(it, ' ') }
                        passwordChars?.let { Arrays.fill(it, ' ') }
                    }
                }

                callback.onSuccess(responseBuilder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Autofill failed", e)
                callback.onFailure(e.message)
            }
        }
    }

    private fun mapFields(node: AssistStructure.ViewNode, username: String, password: String, result: MutableMap<AutofillId, String>) {
        val hints = node.autofillHints?.map { it.lowercase() }
        val idEntry = node.idEntry?.lowercase() ?: ""
        val hint = node.hint?.toString()?.lowercase() ?: ""

        if (hints?.any { it.contains("username") || it.contains("email") } == true || idEntry.contains("username") || idEntry.contains("email") || hint.contains("username") || hint.contains("email")) {
            node.autofillId?.let { result[it] = username }
        } else if (hints?.any { it.contains("password") } == true || idEntry.contains("password") || hint.contains("password")) {
            node.autofillId?.let { result[it] = password }
        }

        for (i in 0 until node.childCount) {
            mapFields(node.getChildAt(i), username, password, result)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.last().structure
        val fields = mutableMapOf<String, CharArray>()
        var webDomain: String? = null
        
        findValuesAndDomain(structure.getWindowNodeAt(0).rootViewNode, fields) { domain ->
            webDomain = domain
        }

        val username = fields["username"]
        val password = fields["password"]

        if (username == null || password == null) {
            callback.onSuccess()
            return
        }

        serviceScope.launch {
            val key = vaultManager.getKey()
            if (key != null) {
                repository.insertPassword(PasswordEntry(
                    serviceName = webDomain ?: "Unknown Service",
                    encryptedUsername = CryptoUtils.encrypt(username, key),
                    encryptedPassword = CryptoUtils.encrypt(password, key),
                    encryptedNotes = CryptoUtils.encrypt(getString(R.string.saved_via_autofill).toCharArray(), key),
                    associatedDomain = webDomain
                ))
            }
        }
        
        // Clean up fields map
        fields.values.forEach { Arrays.fill(it, '0') }
        
        callback.onSuccess()
    }

    private fun findAutofillIdsAndDomain(node: AssistStructure.ViewNode, ids: MutableList<AutofillId>, onDomainFound: (String) -> Unit = {}) {
        node.webDomain?.let { onDomainFound(it) }
        
        if (node.autofillType != android.view.View.AUTOFILL_TYPE_NONE) {
            node.autofillId?.let { ids.add(it) }
        }

        for (i in 0 until node.childCount) {
            findAutofillIdsAndDomain(node.getChildAt(i), ids, onDomainFound)
        }
    }

    private fun findValuesAndDomain(node: AssistStructure.ViewNode, fields: MutableMap<String, CharArray>, onDomainFound: (String) -> Unit) {
        node.webDomain?.let { onDomainFound(it) }
        
        val textValue = node.autofillValue?.textValue
        val hints = node.autofillHints?.map { it.lowercase() }
        val idEntry = node.idEntry?.lowercase() ?: ""
        val inputType = node.inputType
        val hint = node.hint?.toString()?.lowercase() ?: ""
        
        if (textValue != null && textValue.isNotEmpty()) {
            val isPasswordField = hints?.any { it.contains("password") } == true || 
                                 idEntry.contains("password") || 
                                 hint.contains("password") ||
                                 (inputType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_TEXT && 
                                 (inputType and (0x00000ff0)) != 0 

            val valueChars = CharArray(textValue.length)
            for (i in 0 until textValue.length) {
                valueChars[i] = textValue[i]
            }

            if (isPasswordField) {
                fields["password"] = valueChars
            } else {
                fields["username"] = valueChars
            }
        }

        for (i in 0 until node.childCount) {
            findValuesAndDomain(node.getChildAt(i), fields, onDomainFound)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
