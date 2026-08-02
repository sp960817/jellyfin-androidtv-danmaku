package org.jellyfin.androidtv.ui.preference.dsl

import android.content.Context
import androidx.annotation.StringRes
import androidx.preference.EditTextPreference
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
		val pref = EditTextPreference(context).also {
			it.isPersistent = false
			it.key = UUID.randomUUID().toString()
			category.addPreference(it)
			it.isEnabled = dependencyCheckFun() && enabled
			it.isVisible = visible
			it.title = title
			it.dialogTitle = title
			it.text = binder.get()
			it.summary = it.text.orEmpty().ifBlank { content.orEmpty() }
			it.setOnPreferenceChangeListener { _, newValue ->
				binder.set(newValue.toString().trim())
				it.text = binder.get()
				it.summary = it.text.orEmpty().ifBlank { content.orEmpty() }
				container()
				false
			}
		}

		container += {
			pref.isEnabled = dependencyCheckFun() && enabled
		}
	}
}

@OptionsDSL
fun OptionsCategory.text(init: OptionsItemText.() -> Unit) {
	this += OptionsItemText(context).apply { init() }
}
