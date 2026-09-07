package app.myfinhub.android.feature.money

import androidx.compose.ui.unit.Dp

/** Package-local Compose unit compatibility for retained money surfaces. */
internal val Int.dp: Dp
    get() = Dp(toFloat())
