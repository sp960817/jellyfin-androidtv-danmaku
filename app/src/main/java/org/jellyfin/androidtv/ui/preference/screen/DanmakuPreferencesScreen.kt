package org.jellyfin.androidtv.ui.preference.screen

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.preference.custom.DurationSeekBarPreference
import org.jellyfin.androidtv.ui.preference.dsl.OptionsFragment
import org.jellyfin.androidtv.ui.preference.dsl.checkbox
import org.jellyfin.androidtv.ui.preference.dsl.optionsScreen
import org.jellyfin.androidtv.ui.preference.dsl.seekbar
import org.jellyfin.androidtv.ui.preference.dsl.text
import org.koin.android.ext.android.inject
import kotlin.math.roundToInt

class DanmakuPreferencesScreen : OptionsFragment() {
	private val userPreferences: UserPreferences by inject()

	override val screen by optionsScreen {
		setTitle(R.string.pref_danmaku)

		category {
			checkbox {
				setTitle(R.string.pref_danmaku_enabled)
				setContent(R.string.pref_danmaku_enabled_description)
				bind(userPreferences, UserPreferences.danmakuEnabled)
			}

			text {
				setTitle(R.string.pref_danmaku_api_url)
				setContent(R.string.pref_danmaku_api_url_description)
				bind(userPreferences, UserPreferences.danmakuApiUrl)
			}
		}

		category {
			setTitle(R.string.pref_customization)

			seekbar {
				setTitle(R.string.pref_danmaku_font_size)
				min = 16
				max = 40
				increment = 2
				bind(userPreferences, UserPreferences.danmakuFontSize)
			}

			seekbar {
				setTitle(R.string.pref_danmaku_opacity)
				min = 20
				max = 100
				increment = 5
				valueFormatter = percentFormatter
				bind(userPreferences, UserPreferences.danmakuOpacity)
			}

			seekbar {
				setTitle(R.string.pref_danmaku_display_area)
				min = 25
				max = 100
				increment = 25
				valueFormatter = percentFormatter
				bind(userPreferences, UserPreferences.danmakuDisplayArea)
			}

			seekbar {
				setTitle(R.string.pref_danmaku_speed)
				min = 50
				max = 200
				increment = 25
				valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
					override fun display(value: Int) = "${value / 100f}x"
				}
				bind {
					get { (userPreferences[UserPreferences.danmakuSpeed] * 100).roundToInt() }
					set { userPreferences[UserPreferences.danmakuSpeed] = it / 100f }
					default { (UserPreferences.danmakuSpeed.defaultValue * 100).roundToInt() }
				}
			}

			seekbar {
				setTitle(R.string.pref_danmaku_time_offset)
				min = -30_000
				max = 30_000
				increment = 500
				valueFormatter = object : DurationSeekBarPreference.ValueFormatter() {
					override fun display(value: Int) = String.format("%+.1fs", value / 1000f)
				}
				bind {
					get { userPreferences[UserPreferences.danmakuTimeOffset].toInt() }
					set { userPreferences[UserPreferences.danmakuTimeOffset] = it.toLong() }
					default { UserPreferences.danmakuTimeOffset.defaultValue.toInt() }
				}
			}
		}
	}

	private val percentFormatter = object : DurationSeekBarPreference.ValueFormatter() {
		override fun display(value: Int) = "$value%"
	}
}
