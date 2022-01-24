/*
 * Copyright 2021 Eric A. Snell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ealva.toque.prefs

import com.ealva.prefstore.store.BoolPref
import com.ealva.prefstore.store.DoublePref
import com.ealva.prefstore.store.IntPref
import com.ealva.prefstore.store.PreferenceStore
import com.ealva.prefstore.store.PreferenceStoreSingleton
import com.ealva.prefstore.store.Storage
import com.ealva.prefstore.store.StorePref
import com.ealva.toque.art.ArtworkDownloader.CompressionQuality
import com.ealva.toque.common.AllowDuplicates
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_ALLOW_DUPLICATES
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_AUTO_ADVANCE_FADE
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_AUTO_ADVANCE_FADE_LENGTH
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_DUCK_ACTION
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_DUCK_VOLUME
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_END_OF_QUEUE_ACTION
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_GO_TO_NOW_PLAYING
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_IGNORE_SMALL_FILES
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_IGNORE_THRESHOLD
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_MANUAL_ADVANCE_FADE
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_MANUAL_ADVANCE_FADE_LENGTH
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_MARK_PLAYED_PERCENTAGE
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_PLAY_PAUSE_FADE
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_PLAY_PAUSE_FADE_LENGTH
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_PLAY_UP_NEXT_ACTION
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_QUALITY
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_SELECT_MEDIA_ACTION
import com.ealva.toque.prefs.AppPrefs.Companion.DEFAULT_THEME_CHOICE
import com.ealva.toque.prefs.AppPrefs.Companion.DUCK_VOLUME_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.IGNORE_FILES_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.MARK_PLAYED_PERCENTAGE_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.MAX_IMAGE_SEARCH_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.MEDIA_FADE_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.PLAY_PAUSE_FADE_RANGE
import com.ealva.toque.prefs.AppPrefs.Companion.QUALITY_RANGE
import org.koin.core.qualifier.named

typealias AppPrefsSingleton = PreferenceStoreSingleton<AppPrefs>

interface AppPrefs : PreferenceStore<AppPrefs> {
  val firstRun: BoolPref
  val allowDuplicates: BoolPref
  val goToNowPlaying: BoolPref
  val ignoreSmallFiles: BoolPref
  val ignoreThreshold: MillisStorePref
  val lastScanTime: MillisStorePref
  val playPauseFade: BoolPref
  val playPauseFadeLength: MillisStorePref
  val autoAdvanceFade: BoolPref
  val autoAdvanceFadeLength: MillisStorePref
  val manualChangeFade: BoolPref
  val manualChangeFadeLength: MillisStorePref
  val duckAction: StorePref<Int, DuckAction>
  val duckVolume: VolumeStorePref
  val playUpNextAction: StorePref<Int, PlayUpNextAction>
  val endOfQueueAction: StorePref<Int, EndOfQueueAction>
  val selectMediaAction: StorePref<Int, SelectMediaAction>
  val themeChoice: StorePref<Int, ThemeChoice>

  /**
   * This represents a percentage of media which needs to be played before it is marked as played.
   * The range of this value is 0.0..1.0. eg. 0.5 is 50% and 1.0 is 100%
   */
  val markPlayedPercentage: DoublePref
  val rewindThenPrevious: BoolPref
  val scanAfterMediaScanner: BoolPref
  val saveRatingToFile: BoolPref
  val scanInternalVolume: BoolPref
  val showTimeRemaining: BoolPref
  val playOnBluetoothConnection: BoolPref
  val playOnWiredConnection: BoolPref
  val readTagRating: BoolPref
  val readTagSortFields: BoolPref

  val autoFetchArtLocation: BoolPref
  val downloadArt: BoolPref
  val downloadHighResArt: BoolPref
  val maxImageSearch: IntPref
  val downloadUnmeteredOnly: BoolPref
  val compressionQuality: StorePref<Int, CompressionQuality>

  companion object {
    val QUALIFIER = named("AppPrefs")

    val DEFAULT_ALLOW_DUPLICATES = AllowDuplicates(false)
    const val DEFAULT_GO_TO_NOW_PLAYING = true
    const val DEFAULT_IGNORE_SMALL_FILES = false
    val IGNORE_FILES_RANGE: ClosedRange<Millis> = Millis.FIVE_SECONDS..Millis.ONE_MINUTE
    val DEFAULT_IGNORE_THRESHOLD = Millis(20000).coerceIn(IGNORE_FILES_RANGE)
    const val DEFAULT_PLAY_PAUSE_FADE = true
    val PLAY_PAUSE_FADE_RANGE: ClosedRange<Millis> = Millis(500)..Millis(2000)
    val DEFAULT_PLAY_PAUSE_FADE_LENGTH = Millis.ONE_SECOND.coerceIn(PLAY_PAUSE_FADE_RANGE)
    val MEDIA_FADE_RANGE: ClosedRange<Millis> = Millis.ONE_SECOND..Millis.TEN_SECONDS
    const val DEFAULT_AUTO_ADVANCE_FADE = false
    val DEFAULT_AUTO_ADVANCE_FADE_LENGTH = Millis.THREE_SECONDS.coerceIn(MEDIA_FADE_RANGE)
    const val DEFAULT_MANUAL_ADVANCE_FADE = true
    val DEFAULT_MANUAL_ADVANCE_FADE_LENGTH = Millis.TWO_SECONDS.coerceIn(MEDIA_FADE_RANGE)
    val DEFAULT_PLAY_UP_NEXT_ACTION = PlayUpNextAction.Prompt
    val DEFAULT_END_OF_QUEUE_ACTION = EndOfQueueAction.PlayNextList
    val DEFAULT_SELECT_MEDIA_ACTION = SelectMediaAction.Play
    val DEFAULT_THEME_CHOICE = ThemeChoice.System
    val DEFAULT_DUCK_ACTION = DuckAction.Duck
    val DUCK_VOLUME_RANGE: ClosedRange<Volume> = Volume.NONE..Volume.MAX
    val DEFAULT_DUCK_VOLUME: Volume = DUCK_VOLUME_RANGE.endInclusive / 2

    val MARK_PLAYED_PERCENTAGE_RANGE = 0.0..1.0
    const val DEFAULT_MARK_PLAYED_PERCENTAGE: Double = 0.5

    val MAX_IMAGE_SEARCH_RANGE = 10..50

    val DEFAULT_QUALITY = CompressionQuality(80)
    val QUALITY_RANGE = 0..100

    fun make(storage: Storage): AppPrefs = AppPrefsImpl(storage)
  }
}


private class AppPrefsImpl(
  storage: Storage
) : BaseToquePreferenceStore<AppPrefs>(storage), AppPrefs {
  override val firstRun by preference(true)
  override val allowDuplicates by preference(DEFAULT_ALLOW_DUPLICATES.value)
  override val goToNowPlaying by preference(DEFAULT_GO_TO_NOW_PLAYING)
  override val ignoreSmallFiles by preference(DEFAULT_IGNORE_SMALL_FILES)
  override val ignoreThreshold by millisPref(DEFAULT_IGNORE_THRESHOLD) { millis ->
    millis.coerceIn(IGNORE_FILES_RANGE)
  }
  override val lastScanTime by millisPref(Millis(0))
  override val playPauseFade by preference(DEFAULT_PLAY_PAUSE_FADE)
  override val playPauseFadeLength by millisPref(DEFAULT_PLAY_PAUSE_FADE_LENGTH) { millis ->
    millis.coerceIn(PLAY_PAUSE_FADE_RANGE)
  }
  override val autoAdvanceFade by preference(DEFAULT_AUTO_ADVANCE_FADE)
  override val autoAdvanceFadeLength by millisPref(DEFAULT_AUTO_ADVANCE_FADE_LENGTH) { millis ->
    millis.coerceIn(MEDIA_FADE_RANGE)
  }
  override val manualChangeFade by preference(DEFAULT_MANUAL_ADVANCE_FADE)
  override val manualChangeFadeLength by millisPref(DEFAULT_MANUAL_ADVANCE_FADE_LENGTH) { millis ->
    millis.coerceIn(MEDIA_FADE_RANGE)
  }
  override val duckAction by enumPref(DEFAULT_DUCK_ACTION)
  override val duckVolume by volumePref(DEFAULT_DUCK_VOLUME) {
    it.coerceIn(DUCK_VOLUME_RANGE)
  }
  override val playUpNextAction by enumPref(DEFAULT_PLAY_UP_NEXT_ACTION)
  override val endOfQueueAction by enumPref(DEFAULT_END_OF_QUEUE_ACTION)
  override val selectMediaAction by enumPref(DEFAULT_SELECT_MEDIA_ACTION)
  override val themeChoice by enumPref(DEFAULT_THEME_CHOICE)
  override val markPlayedPercentage by preference(DEFAULT_MARK_PLAYED_PERCENTAGE) { percentage ->
    percentage.coerceIn(MARK_PLAYED_PERCENTAGE_RANGE)
  }
  override val rewindThenPrevious: BoolPref by preference(true)
  override val scanAfterMediaScanner: BoolPref by preference(true)
  override val saveRatingToFile: BoolPref by preference(true)
  override val scanInternalVolume: BoolPref by preference(false)
  override val showTimeRemaining: BoolPref by preference(false)
  override val playOnBluetoothConnection: BoolPref by preference(false)
  override val playOnWiredConnection: BoolPref by preference(false)
  override val readTagRating: BoolPref by preference(true)
  override val readTagSortFields: BoolPref by preference(true)

  override val autoFetchArtLocation: BoolPref by preference(false)
  override val downloadArt: BoolPref by preference(true)
  override val downloadHighResArt: BoolPref by preference(false)
  override val maxImageSearch: IntPref by preference(10) {
    it.coerceIn(MAX_IMAGE_SEARCH_RANGE)
  }
  override val downloadUnmeteredOnly: BoolPref by preference(true)
  override val compressionQuality: StorePref<Int, CompressionQuality> by
    asTypePref(DEFAULT_QUALITY, { CompressionQuality(it) }, { it.value }) { quality ->
      CompressionQuality(quality.value.coerceIn(QUALITY_RANGE))
    }

}
