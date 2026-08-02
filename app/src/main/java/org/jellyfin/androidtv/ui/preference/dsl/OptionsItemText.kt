package org.jellyfin.androidtv.ui.preference.dsl

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import java.util.UUID

class OptionsItemText(
	private val context: Context,
) : OptionsItemMutable<String>() {
	var content: String? = null

	fun setTitle(@StringRes resId: Int) {
		title = context.getString(resId)
	}

	fun setContent(@StringRes resId: Int) {
		content = context.getString(resId)
	}

	override fun build(category: PreferenceCategory, container: OptionsUpdateFunContainer) {
		val pref = BindingEditTextPreference(context) { value ->
			binder.set(value.trim())
			container()
		}.also {
			it.isPersistent = false
			it.key = UUID.randomUUID().toString()
			category.addPreference(it)
			it.isEnabled = dependencyCheckFun() && enabled
			it.isVisible = visible
			it.title = title
			it.dialogTitle = title
			it.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
				preference.text.orEmpty().ifBlank { content.orEmpty() }
			}
			it.text = binder.get()
		}

		container += {
			pref.isEnabled = dependencyCheckFun() && enabled
		}
	}
}

/**
 * Leanback's text dialog writes through [EditTextPreference.setText] directly instead of calling
 * the preference change listener. Bind at that API boundary so TV keyboard input is persisted.
 */
internal class BindingEditTextPreference(
	context: Context,
	private val persistValue: (String) -> Unit,
) : EditTextPreference(context) {
	override fun setText(text: String?) {
		val value = text.orEmpty()
		persistValue(value)
		super.setText(value)
	}
}

@OptionsDSL
fun OptionsCategory.text(init: OptionsItemText.() -> Unit) {
	this += OptionsItemText(context).apply { init() }
}
