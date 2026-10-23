package app.myfinhub.android.feature.utilities

/**
 * Web TransactionRulesWorkspace fields, independent of the Android local-preview reducer.
 * A null ID creates a new rule; a non-null ID edits only the corresponding canonical source.
 */
data class TransactionRuleEditRequest(
    val id: String?,
    val name: String,
    val matchDescription: String,
    val matchMerchant: String,
    val matchAccountId: String,
    val matchMode: String,
    val actionCategory: String,
    val actionSubcategory: String,
    val actionNote: String,
    val scopes: List<String>,
)
