package de.roadcam.app

import androidx.annotation.StringRes

data class UiText(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList()
) {
    constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
}
