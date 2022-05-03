/*
 * Copyright 2022 Eric A. Snell
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

package com.ealva.toque.android.service.player

import com.ealva.toque.common.Volume
import com.ealva.toque.service.audio.PlayerTransition
import com.ealva.toque.service.player.FadeInTransition
import com.ealva.toque.service.player.PauseFadeOutTransition
import com.ealva.toque.service.player.PauseImmediateTransition
import com.ealva.toque.service.player.PlayImmediateTransition
import com.ealva.toque.service.player.ShutdownFadeOutTransition
import com.ealva.toque.service.player.ShutdownImmediateTransition
import com.ealva.toque.test.service.player.TransitionPlayerSpy
import com.ealva.toque.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalCoroutinesApi::class)
class FadeInTransitionTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  private lateinit var player: TransitionPlayerSpy
  private lateinit var transition: PlayerTransition

  @Before
  fun init() {
    player = TransitionPlayerSpy()
    transition = FadeInTransition(
      2000.toDuration(DurationUnit.MILLISECONDS),
      dispatcher = coroutineRule.testDispatcher
    )
    transition.setPlayer(player)
  }

  @Test
  fun isPlaying() {
    player._isPlaying = false
    expect(transition.isPlaying).toBe(true) // shouldn't ask the player, always true
  }

  @Test
  fun isPaused() {
    player._isPaused = true
    expect(transition.isPaused).toBe(false) // shouldn't ask the player, always false
  }

  @Test
  fun accept() {
    expect(transition.accept(PlayImmediateTransition())).toBe(false)
    expect(transition.accept(FadeInTransition(Duration.ZERO))).toBe(false)
    expect(transition.accept(ShutdownFadeOutTransition(Duration.ZERO))).toBe(true)
    expect(transition.accept(ShutdownImmediateTransition())).toBe(true)
    expect(transition.accept(PauseImmediateTransition())).toBe(true)
    expect(transition.accept(PauseFadeOutTransition(Duration.ZERO))).toBe(true)
  }

  @Test
  fun execute() = runTest {
    // given
    player._volume = Volume.NONE
    player._isPlaying = false
    player._remainingTime = 2000.toDuration(DurationUnit.MILLISECONDS)

    // when
    transition.execute()
    advanceUntilIdle()

    // then
    expect(player._playCalled).toBe(1)
    expect(player._notifyPlayingCalled).toBe(1)
    expect(player._volumeGetCalled).toBe(1)
    expect(player._remainingTimeCalled).toBe(1)
    expect(player._volumeSetCalled).toBeGreaterThan(0)
    expect(player._volume).toBe(Volume.MAX)
    expect(transition.isCancelled).toBe(false)
    expect(transition.isFinished).toBe(true)
  }

  @Test
  fun cancel() = runTest {
    transition.setCancelled()
    transition.execute()
    player.verifyZeroInteractions()
    expect(transition.isCancelled).toBe(true)
    expect(transition.isFinished).toBe(true)
  }
}
