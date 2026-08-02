package org.jellyfin.androidtv.ui.preference.dsl

import android.os.Bundle
import android.widget.EditText
import androidx.leanback.preference.LeanbackEditTextPreferenceDialogFragmentCompat
import androidx.leanback.preference.LeanbackPreferenceDialogFragmentCompat
import androidx.preference.EditTextPreference

/**
 * Persists text when leaving the TV editor, including when the remote back button is used.
 * The stock Leanback dialog only saves for an IME action and silently drops text on back.
 */
internal class BindingEditTextPreferenceDialogFragment : LeanbackEditTextPreferenceDialogFragmentCompat() {
	override fun onPause() {
		view?.findViewById<EditText>(android.R.id.edit)?.text?.toString()?.let { value ->
			(preference as EditTextPreference).text = value
		}
		super.onPause()
	}

	companion object {
		fun newInstance(key: String) = BindingEditTextPreferenceDialogFragment().apply {
			arguments = Bundle(1).apply {
				putString(LeanbackPreferenceDialogFragmentCompat.ARG_KEY, key)
			}
		}
	}
}
