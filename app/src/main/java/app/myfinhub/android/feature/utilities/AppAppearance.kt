package app.myfinhub.android.feature.utilities

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

enum class AppAppearance(val storageValue: String, val label: String) {
    SYSTEM("system", "Σύστημα"),
    LIGHT("light", "Φωτεινό"),
    DARK("dark", "Σκούρο"),
    ;

    companion object {
        fun fromStorage(value: String?): AppAppearance = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

object AppAppearancePreference {
    const val PREFERENCES_NAME = "myfinhub_local_preferences"
    const val KEY = "appearance"

    fun read(context: Context): AppAppearance = AppAppearance.fromStorage(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).getString(KEY, null),
    )

    fun write(context: Context, appearance: AppAppearance) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, appearance.storageValue)
            .apply()
    }
}

/** Device-local privacy preference. It never enters the canonical finance document. */
object AmountVisibilityPreference {
    const val KEY = "amounts_visible"

    fun read(context: Context): Boolean = context
        .getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY, false)

    fun write(context: Context, visible: Boolean) {
        context.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, visible)
            .apply()
    }
}


internal const val HIDDEN_AMOUNT_TEXT = "•••• €"

internal fun amountVisibilityText(text: String, visible: Boolean): String =
    if (visible) text else HIDDEN_AMOUNT_TEXT

/** Observes the device-local amount-visibility preference so every active read surface updates live. */
@Composable
fun rememberAmountVisibilityPreference(): Boolean {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var visible by remember(context) { mutableStateOf(AmountVisibilityPreference.read(context)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AmountVisibilityPreference.KEY) visible = AmountVisibilityPreference.read(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return visible
}
