/*
 * Copyright 2020 eAlva.com
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

package com.ealva.toque.service.vlc

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.audioout.AudioOutputRoute
import com.ealva.toque.db.DaoExceptionMessage
import com.ealva.toque.db.DaoMessage
import com.ealva.toque.db.EqPresetAssociationDao
import com.ealva.toque.db.EqPresetDao
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.db.EqPresetInfo
import com.ealva.toque.db.NullEqPresetDao
import com.ealva.toque.persist.AlbumId
import com.ealva.toque.persist.MediaId
import com.ealva.toque.service.media.EqPreset
import com.ealva.toque.service.media.EqPresetFactory
import com.ealva.toque.service.media.EqPresetFactory.Companion.DEFAULT_SYSTEM_PRESET_NAME
import com.ealva.toque.service.vlc.VlcEqPreset.Companion.setNativeEqValues
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import org.videolan.libvlc.MediaPlayer

private val LOG by lazyLogger(VlcPresetFactory::class)

class VlcPresetFactory(
  private val eqPresetDao: EqPresetDao,
  private val eqPresetAssocDao: EqPresetAssociationDao
) : EqPresetFactory {
  override val bandCount: Int
    get() = MediaPlayer.Equalizer.getBandCount()
  override val bandIndices: IntRange by lazy { 0 until bandCount }
  override val systemPresetCount: Int
    get() = presetCount

  override suspend fun getAllPresets(): List<EqPresetInfo> {
    TODO("Not yet implemented")
  }

  override suspend fun getPreset(id: EqPresetId): Result<EqPreset, DaoMessage> =
    runCatching {
      if (id.value in 0..systemPresetCount) {
        VlcEqPreset(
          MediaPlayer.Equalizer.createFromPreset(id.value.toInt()),
          MediaPlayer.Equalizer.getPresetName(id.value.toInt()),
          true,
          id,
          NullEqPresetDao
        )
      } else {
        when (val result = eqPresetDao.getPresetData(id)) {
          is Ok -> {
            val data = result.value
            VlcEqPreset(
              MediaPlayer.Equalizer.create().setNativeEqValues(data.preAmpAndBands),
              data.name,
              false,
              data.id,
              eqPresetDao
            )
          }
          is Err -> throw NoSuchElementException("PresetId:$id")
        }
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun makeFrom(eqPreset: EqPreset, name: String): Result<EqPreset, DaoMessage> {
    val preAmpAndBands = eqPreset.getAllValues()
    return when (val result = eqPresetDao.insertPreset(name, preAmpAndBands)) {
      is Ok -> {
        runCatching {
          VlcEqPreset(
            MediaPlayer.Equalizer.create().apply { setNativeEqValues(preAmpAndBands) },
            name,
            false,
            result.value,
            eqPresetDao
          )
        }.mapError { DaoExceptionMessage(it) }
      }
      is Err -> Err(result.error)
    }
  }

  override suspend fun getPreferred(
    mediaId: MediaId,
    albumId: AlbumId,
    outputRoute: AudioOutputRoute
  ): Result<EqPreset, DaoMessage> = doGetPreferred(mediaId, albumId, outputRoute)

  private suspend fun doGetPreferred(
    mediaId: MediaId,
    albumId: AlbumId,
    route: AudioOutputRoute
  ): Result<EqPreset, DaoMessage> = when (
    val result = eqPresetAssocDao.getPreferredId(mediaId, albumId, route, defaultId)
  ) {
    is Ok -> getPreset(result.value)
    is Err -> {
      LOG.e { it("getPreferred ${result.error}") }
      getPreset(defaultId)
    }
  }

  override val nonePreset: EqPreset
    get() = VlcEqPreset.NONE

  companion object {
    private val presetCount: Int
      get() = MediaPlayer.Equalizer.getPresetCount()

    /** Try to set the default to "Flat" system preset */
    private var systemDefaultId = EqPresetId(-1L)
    private val defaultId: EqPresetId
      get() {
        if (systemDefaultId.value == -1L) {
          for (i in 0 until presetCount) {
            if (nameOf(i).equals(DEFAULT_SYSTEM_PRESET_NAME, ignoreCase = true))
              systemDefaultId = EqPresetId(i)
          }
          if (systemDefaultId.value == -1L) systemDefaultId = EqPresetId(0)
        }
        return systemDefaultId
      }

    private fun nameOf(i: Int) = MediaPlayer.Equalizer.getPresetName(i)
  }
}
