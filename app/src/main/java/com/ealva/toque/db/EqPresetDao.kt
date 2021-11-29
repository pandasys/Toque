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

package com.ealva.toque.db

import com.ealva.toque.common.Amp
import com.ealva.toque.common.EqPresetId
import com.ealva.toque.common.Millis
import com.ealva.toque.service.media.PreAmpAndBands
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.SQLiteSequence
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError

interface EqPresetDao {
  /**
   * Insert the preset name and all preset data. Returns the preset ID if successful
   */
  suspend fun insertPreset(name: String, preAmpAndBands: PreAmpAndBands): DaoResult<EqPresetId>

  /**
   * Get all the data for [id]
   */
  suspend fun getPresetData(id: EqPresetId): Result<EqPresetData, DaoMessage>

  /**
   * Get all user defined presets
   */
  suspend fun getAllUserPresets(): Result<List<EqPresetIdName>, DaoMessage>

  /**
   * Update all the preset data the preset with id=```presetData.id```. [duringTxn] is called
   * if the operation was successful and within the transaction boundary. If [duringTxn] throws
   * an exception the transaction is rolled back
   */
  suspend fun updatePreset(presetData: EqPresetData, duringTxn: () -> Unit = {}): BoolResult

  /**
   * Update [id]'s PreAmp [amplitude]
   */
  suspend fun updatePreAmp(id: EqPresetId, amplitude: Amp): BoolResult

  /**
   * Update [id]'s band [amplitude] at band column [index]
   */
  suspend fun updateBand(id: EqPresetId, index: Int, amplitude: Amp): BoolResult

  suspend fun deletePreset(id: EqPresetId): BoolResult

  companion object {
    operator fun invoke(db: Database): EqPresetDao = EqPresetDaoImpl(db)

    val MIN_USER_PRESET_ID = EqPresetId(1000L)
    val BAND_COUNT = EqPresetTable.bandColumns.size

    /**
     * Establishes a minimum row id in the EqPresetTable so user preset IDs do not collide with
     * system preset IDs. If a row id does not exist, inserts a dummy entry into the EqPreset table,
     * establishes the minimum row id, then deletes the dummy record.
     *
     * The [EqPresetTable] must have been created before calling this function. Expected to be
     * called when the DB is created or opened. Call must be idempotent so it can be called from
     * DB open
     */
    fun establishMinimumRowId(txn: TransactionInProgress) = txn.run {
      check(EqPresetTable.exists) { "EqPresetTable must have been created" }
      val tableName = SQLiteSequence
        .select { name }
        .where { name eq EqPresetTable.tableName }
        .sequence { it[name] }
        .singleOrNull()
      if (tableName == null) {
        val dummyName = "DummyDeleteMeDummy"
        val id = EqPresetTable.insert { it[presetName] = dummyName }
        try {
          if (id < MIN_USER_PRESET_ID.value) { // should always be true if tableName was null
            SQLiteSequence
              .updateColumns { it[seq] = (MIN_USER_PRESET_ID.value - 1) }
              .where { name eq EqPresetTable.tableName }
              .update()
          }
        } finally {
          EqPresetTable.delete { presetName eq dummyName }
        }
      }
    }
  }
}

private class EqPresetDaoImpl(
  private val db: Database
) : EqPresetDao {
  override suspend fun insertPreset(
    name: String,
    preAmpAndBands: PreAmpAndBands
  ): DaoResult<EqPresetId> = runSuspendCatching {
    db.transaction {
      EqPresetId(INSERT_PRESET.insert {
        it[presetName] = name
        it[updatedTime] = Millis.currentTime().value
        it[preAmp] = preAmpAndBands.preAmp()
        with(preAmpAndBands) {
          it[band0] = bands[0]()
          it[band1] = bands[1]()
          it[band2] = bands[2]()
          it[band3] = bands[3]()
          it[band4] = bands[4]()
          it[band5] = bands[5]()
          it[band6] = bands[6]()
          it[band7] = bands[7]()
          it[band8] = bands[8]()
          it[band9] = bands[9]()
        }
      })
    }
  }.mapError { DaoExceptionMessage(it) }
    .andThen { if (it.value > 0) Ok(it) else Err(DaoFailedToInsert("$name $preAmpAndBands")) }

  override suspend fun getPresetData(id: EqPresetId): Result<EqPresetData, DaoMessage> =
    runSuspendCatching { db.query { doGetPreset(id.value) } }
      .mapError { DaoExceptionMessage(it) }
      .andThen { it?.let { data -> Ok(data) } ?: Err(DaoNotFound("Preset ID:$id")) }

  private fun Queryable.doGetPreset(presetId: Long): EqPresetData? =
    EqPresetTable
      .select()
      .where { id eq presetId }
      .sequence { EqPresetData(EqPresetId(it[id]), it[presetName], dataFromCursor(it)) }
      .singleOrNull()

  private fun EqPresetTable.dataFromCursor(cursor: Cursor) = PreAmpAndBands(
    Amp(cursor[preAmp]),
    Array(bandColumns.size) { index -> Amp(cursor[bandColumns[index]]) }
  )

  override suspend fun getAllUserPresets(): Result<List<EqPresetIdName>, DaoMessage> =
    runSuspendCatching {
      db.query {
        EqPresetTable
          .selectAll()
          .sequence { EqPresetIdName(EqPresetId(it[id]), it[presetName]) }
          .toList()
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun updatePreset(presetData: EqPresetData, duringTxn: () -> Unit): BoolResult =
    runSuspendCatching {
      db.transaction { doUpdatePreset(presetData).also { if (it > 0) duringTxn() } }
    }.mapError { DaoExceptionMessage(it) }
      .andThen { if (it > 0) Ok(true) else selectUpdateError(presetData.id, "$presetData") }

  private fun TransactionInProgress.doUpdatePreset(presetData: EqPresetData) = UPDATE_ALL.update {
    it[BIND_PRESET_ID] = presetData.id.value
    it[updatedTime] = Millis.currentTime().value
    it[preAmp] = presetData.preAmpAndBands.preAmp()
    with(presetData.preAmpAndBands) {
      it[band0] = bands[0]()
      it[band1] = bands[1]()
      it[band2] = bands[2]()
      it[band3] = bands[3]()
      it[band4] = bands[4]()
      it[band5] = bands[5]()
      it[band6] = bands[6]()
      it[band7] = bands[7]()
      it[band8] = bands[8]()
      it[band9] = bands[9]()
    }
  }

  override suspend fun updatePreAmp(
    id: EqPresetId,
    amplitude: Amp
  ): BoolResult = runSuspendCatching {
    db.transaction {
      EqPresetTable
        .updateColumns {
          it[preAmp] = amplitude()
          it[updatedTime] = Millis.currentTime().value
        }
        .where { this.id eq id.value }
        .update()
    }
  }.mapError { DaoExceptionMessage(it) }
    .andThen { if (it > 0) Ok(true) else selectUpdateError(id, "PresetId:$id preamp:$amplitude") }

  override suspend fun updateBand(
    id: EqPresetId,
    index: Int,
    amplitude: Amp
  ): BoolResult = runSuspendCatching {
    db.transaction {
      val columnIndices = EqPresetTable.bandColumns.indices
      require(index in columnIndices) { "Index $index not in bounds $columnIndices" }
      EqPresetTable.updateColumns {
        it[bandColumns[index]] = amplitude()
        it[updatedTime] = Millis.currentTime().value
      }.where {
        this.id eq id.value
      }.update()
    }
  }.mapError {
    DaoExceptionMessage(it)
  }.andThen {
    if (it > 0) Ok(true)
    else selectUpdateError(id, "PresetId:$id band:$index value:$amplitude")
  }

  override suspend fun deletePreset(id: EqPresetId): BoolResult = runSuspendCatching {
    db.transaction { EqPresetTable.delete { this.id eq id.value } }
  }.mapError {
    DaoExceptionMessage(it)
  }.andThen {
    if (it > 0) Ok(true)
    else {
      selectErrorIfExists(
        id,
        DaoFailedToDelete("PresetId:$id"),
        DaoNotFound("PresetId:$id")
      )
    }
  }

  private suspend fun selectUpdateError(id: EqPresetId, msg: String): BoolResult =
    selectErrorIfExists(id, DaoNotFound("Preset ID:$id"), DaoFailedToUpdate(msg))

  /**
   * These error conditions shouldn't occur in practice so another txn to narrow down this issue
   * is not a big deal
   */
  private suspend fun selectErrorIfExists(
    presetId: EqPresetId,
    existsMsg: DaoMessage,
    notExistsMsg: DaoMessage
  ): Result<Nothing, DaoMessage> = runSuspendCatching { db.query { presetExists(presetId) } }
    .mapError { DaoExceptionMessage(it) }
    .andThen { exists ->
      if (exists) Err(existsMsg) else Err(notExistsMsg)
    }

  private fun Queryable.presetExists(presetId: EqPresetId): Boolean =
    EqPresetTable.select().where { id eq presetId.value }.count() > 0
}

data class EqPresetIdName(val id: EqPresetId, val name: String)

data class EqPresetData(
  val id: EqPresetId,
  val name: String,
  val preAmpAndBands: PreAmpAndBands
)

private val BIND_PRESET_ID = EqPresetTable.id.bindArg()
private val UPDATE_ALL = EqPresetTable.updateColumns {
  it[preAmp].bindArg()
  it[band0].bindArg()
  it[band1].bindArg()
  it[band2].bindArg()
  it[band3].bindArg()
  it[band4].bindArg()
  it[band5].bindArg()
  it[band6].bindArg()
  it[band7].bindArg()
  it[band8].bindArg()
  it[band9].bindArg()
  it[updatedTime].bindArg()
}.where {
  id eq BIND_PRESET_ID
}

private val INSERT_PRESET = EqPresetTable.insertValues {
  it[presetName].bindArg()
  it[preAmp].bindArg()
  it[band0].bindArg()
  it[band1].bindArg()
  it[band2].bindArg()
  it[band3].bindArg()
  it[band4].bindArg()
  it[band5].bindArg()
  it[band6].bindArg()
  it[band7].bindArg()
  it[band8].bindArg()
  it[band9].bindArg()
  it[updatedTime].bindArg()
}

object NullEqPresetDao : EqPresetDao {
  override suspend fun insertPreset(
    name: String,
    preAmpAndBands: PreAmpAndBands
  ): DaoResult<EqPresetId> = NotImplemented

  override suspend fun getPresetData(
    id: EqPresetId
  ): Result<EqPresetData, DaoMessage> = NotImplemented

  override suspend fun getAllUserPresets(): Result<List<EqPresetIdName>, DaoMessage> =
    NotImplemented

  override suspend fun updatePreset(
    presetData: EqPresetData,
    duringTxn: () -> Unit
  ): BoolResult = NotImplemented

  override suspend fun updatePreAmp(id: EqPresetId, amplitude: Amp): BoolResult = NotImplemented

  override suspend fun updateBand(
    id: EqPresetId,
    index: Int,
    amplitude: Amp
  ): BoolResult = NotImplemented

  override suspend fun deletePreset(id: EqPresetId): BoolResult = NotImplemented

  private val NotImplemented = Err(DaoNotImplemented)
}
