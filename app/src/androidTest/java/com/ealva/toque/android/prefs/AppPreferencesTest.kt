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

package com.ealva.toque.android.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.prefstore.store.invoke
import com.ealva.toque.common.Millis
import com.ealva.toque.common.Volume
import com.ealva.toque.prefs.AppPrefs
import com.ealva.toque.prefs.AppPrefs.Companion.PLAY_PAUSE_FADE_RANGE
import com.ealva.toque.prefs.AppPrefsSingleton
import com.ealva.toque.prefs.DuckAction
import com.ealva.toque.prefs.EndOfQueueAction
import com.ealva.toque.prefs.PlayUpNextAction
import com.ealva.toque.prefs.SelectMediaAction
import com.ealva.toque.test.shared.CoroutineRule
import com.ealva.toque.test.shared.toNotBe
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AppPreferencesTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  @get:Rule
  val tempFolder: TemporaryFolder = TemporaryFolder()

  private lateinit var appCtx: Context
  private lateinit var testFile: File
  private lateinit var dataStoreScope: TestScope
  private lateinit var prefsSingleton: AppPrefsSingleton

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
    testFile = tempFolder.newFile("dummy.preferences_pb")
    dataStoreScope = TestScope(coroutineRule.testDispatcher + Job())
    prefsSingleton = AppPrefsSingleton(AppPrefs.Companion::make, testFile, dataStoreScope)
  }

  @Test
  fun testFirstRunWithPermission() = runTest {
    prefsSingleton {
      expect(firstRun()).toBe(firstRun.default)
      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testAllowDuplicates() = runTest {
    prefsSingleton {
      expect(allowDuplicates()).toBe(allowDuplicates.default)
      allowDuplicates.set(true)
      expect(allowDuplicates()).toBe(true)
      allowDuplicates.set(false)
      expect(allowDuplicates()).toBe(false)

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testGoToNowPlaying() = runTest {
    prefsSingleton {
      expect(goToNowPlaying()).toBe(goToNowPlaying.default)
      goToNowPlaying.set(false)
      expect(goToNowPlaying()).toBe(false)
      goToNowPlaying.set(true)
      expect(goToNowPlaying()).toBe(true)

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testIgnoreSmallFiles() = runTest {
    prefsSingleton {
      expect(ignoreSmallFiles()).toBe(ignoreSmallFiles.default)
      ignoreSmallFiles.set(true)
      expect(ignoreSmallFiles()).toBe(true)

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testIgnoreThreshold() = runTest {
    prefsSingleton {
      expect(ignoreThreshold()).toBe(ignoreThreshold.default)
      ignoreThreshold.set(Millis(5000))
      expect(ignoreThreshold()).toBe(Millis(5000))

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testLastScanTime() = runTest {
    prefsSingleton {
      // we never reset last scan time to default of DEFAULT_LAST_SCAN_TIME, so let's not test that
      lastScanTime.set(Millis.ONE_MINUTE)
      expect(lastScanTime()).toBe(Millis.ONE_MINUTE)
      lastScanTime.set(lastScanTime.default)
      expect(lastScanTime()).toBe(lastScanTime.default)

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testFadeOnPlayPause() = runTest {
    prefsSingleton {
      expect(playPauseFade()).toBe(playPauseFade.default)
      playPauseFade.set(true)
      expect(playPauseFade()).toBe(true)

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testPlayPauseFade() = runTest {
    prefsSingleton {
      expect(playPauseFadeLength()).toBe(playPauseFadeLength.default)
      playPauseFadeLength.set(Millis(1000))
      expect(playPauseFadeLength()).toBe(Millis(1000))
      playPauseFadeLength.set(Millis(2000))
      expect(playPauseFadeLength()).toBe(Millis(2000))
      playPauseFadeLength.set(Millis(100))
      expect(playPauseFadeLength()).toBe(PLAY_PAUSE_FADE_RANGE.start)
      playPauseFadeLength.set(Millis(5000))
      expect(playPauseFadeLength()).toBe(PLAY_PAUSE_FADE_RANGE.endInclusive)

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testDuckAction() = runTest {
    prefsSingleton {
      expect(duckAction()).toBe(duckAction.default)
      DuckAction.values().forEach { action ->
        duckAction.set(action)
        expect(duckAction()).toBe(action)
      }

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testDuckVolume() = runTest {
    prefsSingleton {
      expect(duckVolume()).toBe(duckVolume.default)
      duckVolume.set(Volume(-100))
      expect(duckVolume()).toBe(Volume.NONE)
      duckVolume.set(Volume(1000))
      expect(duckVolume()).toBe(Volume.MAX)
      arrayOf(Volume(10), Volume(30), Volume(67), Volume(91)).forEach { vol ->
        duckVolume.set(vol)
        expect(duckVolume()).toBe(vol)
      }

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testPlayUpNextAction() = runTest {
    prefsSingleton {
      expect(playUpNextAction()).toBe(playUpNextAction.default)
      PlayUpNextAction.values().forEach { action ->
        playUpNextAction.set(action)
        expect(playUpNextAction()).toBe(action)
      }

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testEndOfQueueAction() = runTest {
    prefsSingleton {
      expect(endOfQueueAction()).toBe(endOfQueueAction.default)
      EndOfQueueAction.values().forEach { action ->
        endOfQueueAction.set(action)
        expect(endOfQueueAction()).toBe(action)
      }

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testSelectMediaAction() = runTest {
    prefsSingleton {
      expect(selectMediaAction()).toBe(selectMediaAction.default)
      SelectMediaAction.values().forEach { action ->
        selectMediaAction.set(action)
        expect(selectMediaAction()).toBe(action)
      }

      clear()
      expectAllAreDefault()
    }
  }

  @Test
  fun testResetAllToDefault() = runTest {
    prefsSingleton {
      clear()
      expectAllAreDefault()
      firstRun(!firstRun.default)
      allowDuplicates(!allowDuplicates.default)
      goToNowPlaying(!goToNowPlaying.default)
      ignoreSmallFiles(!ignoreSmallFiles.default)
      ignoreThreshold(ignoreThreshold.default + 1000L)
      playPauseFade(!playPauseFade.default)
      playPauseFadeLength(playPauseFadeLength.default + 500L)
      duckAction(DuckAction.Pause)
      duckVolume(duckVolume.default + 1)
      playUpNextAction(PlayUpNextAction.ClearUpNext)
      endOfQueueAction(EndOfQueueAction.Stop)
      selectMediaAction(SelectMediaAction.AddToUpNext)
      expectAllAreNotDefault()
      clear()
      expectAllAreDefault()
    }
  }

  private fun AppPrefs.expectAllAreDefault() {
    expect(firstRun()).toBe(firstRun.default)
    expect(lastScanTime()).toBe(lastScanTime.default)
    expect(allowDuplicates()).toBe(allowDuplicates.default)
    expect(goToNowPlaying()).toBe(goToNowPlaying.default)
    expect(ignoreSmallFiles()).toBe(ignoreSmallFiles.default)
    expect(ignoreThreshold()).toBe(ignoreThreshold.default)
    expect(playPauseFade()).toBe(playPauseFade.default)
    expect(playPauseFadeLength()).toBe(playPauseFadeLength.default)
    expect(duckAction()).toBe(duckAction.default)
    expect(duckVolume()).toBe(duckVolume.default)
    expect(playUpNextAction()).toBe(playUpNextAction.default)
    expect(selectMediaAction()).toBe(selectMediaAction.default)
  }

  private fun AppPrefs.expectAllAreNotDefault() {
    expect(firstRun()).toNotBe(firstRun.default)
    expect(allowDuplicates()).toNotBe(allowDuplicates.default)
    expect(goToNowPlaying()).toNotBe(goToNowPlaying.default)
    expect(ignoreSmallFiles()).toNotBe(ignoreSmallFiles.default)
    expect(ignoreThreshold()).toNotBe(ignoreThreshold.default)
    expect(playPauseFade()).toNotBe(playPauseFade.default)
    expect(playPauseFadeLength()).toNotBe(playPauseFadeLength.default)
    expect(duckAction()).toNotBe(duckAction.default)
    expect(duckVolume()).toNotBe(duckVolume.default)
    expect(playUpNextAction()).toNotBe(playUpNextAction.default)
    expect(selectMediaAction()).toNotBe(selectMediaAction.default)
  }
}
