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

import android.net.Uri
import com.ealva.ealvabrainz.brainz.data.ArtistMbid
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.common.Filter
import com.ealva.toque.common.Filter.Companion.NoFilter
import com.ealva.toque.common.Limit
import com.ealva.toque.common.Limit.Companion.NoLimit
import com.ealva.toque.common.Millis
import com.ealva.toque.db.DaoCommon.ESC_CHAR
import com.ealva.toque.file.toUriOrEmpty
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.persist.asArtistId
import com.ealva.welite.db.Database
import com.ealva.welite.db.Queryable
import com.ealva.welite.db.TransactionInProgress
import com.ealva.welite.db.compound.union
import com.ealva.welite.db.expr.BindExpression
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Order
import com.ealva.welite.db.expr.bindLong
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.escape
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.like
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.expr.max
import com.ealva.welite.db.expr.min
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.statements.DeleteStatement
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.statements.updateColumns
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.alias
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.by
import com.ealva.welite.db.table.countDistinct
import com.ealva.welite.db.table.groupBy
import com.ealva.welite.db.table.inSubQuery
import com.ealva.welite.db.table.orderBy
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.orderByRandom
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.where
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(ArtistDao::class)

data class ArtistDescription(
  val artistId: ArtistId,
  val name: ArtistName,
  val artwork: Uri,
  val albumCount: Long,
  val songCount: Long
)

data class ArtistIdName(val artistId: ArtistId, val artistName: ArtistName)

sealed class ArtistDaoEvent {
  data class ArtistCreated(val artistId: ArtistId) : ArtistDaoEvent()
  data class ArtistUpdated(val artistId: ArtistId) : ArtistDaoEvent()
}

/**
 * If a function receives a transaction parameter it is not suspending, whereas suspend functions
 * are expected to start transaction or query which will dispatch on another thread, should return a
 * [Result] if not returningUnit and not throw exceptions. Functions receiving a transaction are
 * typically called by the media scanner, directly or indirectly, and are already dispatched on a
 * background thread.
 */
interface ArtistDao {
  val artistDaoEvents: SharedFlow<ArtistDaoEvent>

  /**
   * Update the artist info if it exists otherwise insert it
   */
  fun upsertArtist(
    txn: TransactionInProgress,
    artistName: String,
    artistSort: String,
    artistMbid: ArtistMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): ArtistId

  fun deleteAll(txn: TransactionInProgress): Long
  fun deleteArtistsWithNoMedia(txn: TransactionInProgress): Long
  suspend fun getAlbumArtists(
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): Result<List<ArtistDescription>, DaoMessage>

  suspend fun getSongArtists(
    filter: Filter = NoFilter,
    limit: Limit = NoLimit
  ): Result<List<ArtistDescription>, DaoMessage>

  suspend fun getAllArtistNames(limit: Limit = NoLimit): Result<List<ArtistIdName>, DaoMessage>
  suspend fun getNextArtist(name: ArtistName): Result<ArtistIdName, DaoMessage>
  suspend fun getPreviousArtist(name: ArtistName): Result<ArtistIdName, DaoMessage>

  suspend fun getNext(id: ArtistId): Result<ArtistId, DaoMessage>
  suspend fun getPrevious(id: ArtistId): Result<ArtistId, DaoMessage>
  suspend fun getMin(): Result<ArtistId, DaoMessage>
  suspend fun getMax(): Result<ArtistId, DaoMessage>
  suspend fun getRandom(): Result<ArtistId, DaoMessage>

  suspend fun getRandomArtist(): Result<ArtistIdName, DaoMessage>
  suspend fun getArtistName(id: ArtistId): Result<ArtistName, DaoMessage>

  companion object {
    operator fun invoke(
      db: Database,
      dispatcher: CoroutineDispatcher? = null
    ): ArtistDao = ArtistDaoImpl(db, dispatcher ?: Dispatchers.Main)
  }
}

private class ArtistDaoImpl(private val db: Database, dispatcher: CoroutineDispatcher) : ArtistDao {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
  private val upsertLock: Lock = ReentrantLock()

  override val artistDaoEvents = MutableSharedFlow<ArtistDaoEvent>()

  private fun emit(event: ArtistDaoEvent) {
    scope.launch { artistDaoEvents.emit(event) }
  }

  override fun upsertArtist(
    txn: TransactionInProgress,
    artistName: String,
    artistSort: String,
    artistMbid: ArtistMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): ArtistId = upsertLock.withLock {
    require(artistName.isNotBlank()) { "Artist may not be blank" }
    txn.doUpsertArtist(artistName, artistSort, artistMbid, createUpdateTime, upsertResults)
  }

  private fun TransactionInProgress.doUpsertArtist(
    newArtist: String,
    newArtistSort: String,
    newArtistMbid: ArtistMbid?,
    createUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): ArtistId = try {
    maybeUpdateArtist(
      newArtist,
      newArtistSort,
      newArtistMbid,
      createUpdateTime,
      upsertResults
    ) ?: INSERT_STATEMENT.insert {
      it[artistName] = newArtist
      it[artistSort] = newArtistSort
      it[artistMbid] = newArtistMbid?.value ?: ""
      it[createdTime] = createUpdateTime()
      it[updatedTime] = createUpdateTime()
    }
      .asArtistId
      .also { id -> upsertResults.alwaysEmit { emit(ArtistDaoEvent.ArtistCreated(id)) } }
  } catch (e: Exception) {
    LOG.e(e) { it("Exception with artist='%s'", newArtist) }
    throw e
  }

  /**
   * Update the artist if necessary and return the ArtistId. Null is returned if the artist does not
   * exist
   */
  private fun TransactionInProgress.maybeUpdateArtist(
    newArtist: String,
    newArtistSort: String,
    newArtistMbid: ArtistMbid?,
    newUpdateTime: Millis,
    upsertResults: AudioUpsertResults
  ): ArtistId? = queryArtistUpdateInfo(newArtist)?.let { info ->
    // artist could match on query yet differ in case, so update if case changes
    val updateArtist = info.artist.updateOrNull { newArtist }
    val updateSort = info.artistSort.updateOrNull { newArtistSort }
    val updateMbid = info.artistMbid.updateOrNull { newArtistMbid }

    val updateNeeded = anyNotNull {
      arrayOf(updateArtist, updateSort, updateMbid)
    }

    if (updateNeeded) {
      val updated = ArtistTable.updateColumns {
        updateArtist?.let { update -> it[artistName] = update }
        updateSort?.let { update -> it[artistSort] = update }
        updateMbid?.let { update -> it[artistMbid] = update.value }
        it[updatedTime] = newUpdateTime()
      }.where { id eq info.id.value }.update()

      if (updated >= 1) upsertResults.alwaysEmit { emitUpdated(info.id) } else {
        LOG.e { it("Could not update $info") }
        upsertResults.emitIfMediaCreated { emitUpdated(info.id) }
      }
    } else upsertResults.emitIfMediaCreated { emitUpdated(info.id) }

    info.id
  }

  private fun emitUpdated(artistId: ArtistId) {
    emit(ArtistDaoEvent.ArtistUpdated(artistId))
  }

  private fun Queryable.queryArtistUpdateInfo(
    artistName: String
  ): ArtistUpdateInfo? = QUERY_ARTIST_UPDATE_INFO
    .sequence({ it[queryArtistBind] = artistName }) {
      ArtistUpdateInfo(
        it[id].asArtistId,
        it[this.artistName],
        it[artistSort],
        ArtistMbid(it[artistMbid])
      )
    }.singleOrNull()

  override fun deleteAll(txn: TransactionInProgress): Long = txn.run {
    ArtistTable.deleteAll()
  }

  override fun deleteArtistsWithNoMedia(txn: TransactionInProgress): Long = txn.run {
    DELETE_ARTISTS_WITH_NO_MEDIA.delete()
  }

  override suspend fun getAlbumArtists(
    filter: Filter,
    limit: Limit
  ): Result<List<ArtistDescription>, DaoMessage> = runSuspendCatching {
    db.query { doGetAlbumArtists(filter, limit) }
  }.mapError { DaoExceptionMessage(it) }

  private fun Queryable.doGetAlbumArtists(filter: Filter, limit: Limit): List<ArtistDescription> {
    return ArtistTable
      .join(ArtistAlbumTable, JoinType.INNER, ArtistTable.id, ArtistAlbumTable.artistId)
      .join(AlbumTable, JoinType.INNER, ArtistAlbumTable.albumId, AlbumTable.id)
      .join(MediaTable, JoinType.INNER, ArtistTable.id, MediaTable.albumArtistId)
      .selects {
        listOf(
          ArtistTable.id,
          ArtistTable.artistName,
          ArtistTable.artistImage,
          songCountColumn,
          albumArtistAlbumCountColumn
        )
      }
      .where { filter.whereCondition() }
      .groupBy { ArtistTable.artistSort }
      .orderByAsc { ArtistTable.artistSort }
      .limit(limit.value)
      .sequence {
        ArtistDescription(
          ArtistId(it[ArtistTable.id]),
          ArtistName(it[ArtistTable.artistName]),
          it[ArtistTable.artistImage].toUriOrEmpty(),
          it[albumArtistAlbumCountColumn],
          it[songCountColumn]
        )
      }
      .toList()
  }

  override suspend fun getSongArtists(
    filter: Filter,
    limit: Limit
  ): Result<List<ArtistDescription>, DaoMessage> = runSuspendCatching {
    db.query { doGetSongArtists(filter, limit) }
  }.mapError { DaoExceptionMessage(it) }

  private fun Queryable.doGetSongArtists(filter: Filter, limit: Limit): List<ArtistDescription> {
    return ArtistTable
      .join(ArtistMediaTable, JoinType.INNER, ArtistTable.id, ArtistMediaTable.artistId)
      .join(MediaTable, JoinType.INNER, ArtistMediaTable.mediaId, MediaTable.id)
      .join(AlbumTable, JoinType.INNER, MediaTable.albumId, AlbumTable.id)
      .selects {
        listOf(
          ArtistTable.id,
          ArtistTable.artistName,
          ArtistTable.artistImage,
          songCountColumn,
          songArtistAlbumCountColumn
        )
      }
      .where { filter.whereCondition() }
      .groupBy { ArtistTable.artistSort }
      .orderByAsc { ArtistTable.artistSort }
      .limit(limit.value)
      .sequence {
        ArtistDescription(
          ArtistId(it[ArtistTable.id]),
          ArtistName(it[ArtistTable.artistName]),
          it[ArtistTable.artistImage].toUriOrEmpty(),
          it[songArtistAlbumCountColumn],
          it[songCountColumn]
        )
      }
      .toList()
  }

  private fun Filter.whereCondition() =
    if (isEmpty) null else ArtistTable.artistName like value escape ESC_CHAR

  override suspend fun getAllArtistNames(limit: Limit): Result<List<ArtistIdName>, DaoMessage> =
    runSuspendCatching {
      db.query {
        ArtistTable
          .selects { listOf(id, artistName) }
          .all()
          .orderByAsc { artistSort }
          .limit(limit.value)
          .sequence { ArtistIdName(ArtistId(it[id]), ArtistName(it[artistName])) }
          .toList()
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun getNextArtist(name: ArtistName): Result<ArtistIdName, DaoMessage> =
    runSuspendCatching {
      db.query {
        ArtistTable
          .selects { listOf(id, artistName) }
          .where { artistName greater name.value }
          .orderByAsc { artistSort }
          .limit(1)
          .sequence { ArtistIdName(ArtistId(it[id]), ArtistName(it[artistName])) }
          .single()
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun getPreviousArtist(name: ArtistName) = runSuspendCatching {
    db.query { if (name.isEmpty()) doGetMaxArtist() else doGetPreviousArtist(name) }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getNext(id: ArtistId) = runSuspendCatching {
    db.query {
      ArtistTable
        .select(ArtistTable.id)
        .where { artistName greater SELECT_NAME_FROM_BIND_ID }
        .orderByAsc { artistName }
        .limit(1)
        .longForQuery { it[BIND_ARTIST_ID] = id.value}
        .asArtistId
    }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getPrevious(id: ArtistId) = runSuspendCatching {
    db.query {
      ArtistTable
        .select(ArtistTable.id)
        .where { artistName greater SELECT_NAME_FROM_BIND_ID }
        .orderBy { artistName by Order.DESC }
        .limit(1)
        .longForQuery { it[BIND_ARTIST_ID] = id.value}
        .asArtistId
    }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getMin() = runSuspendCatching {
    db.query {
      ArtistTable
        .selects { listOf(id, artistMin) }
        .all()
        .limit(1)
        .sequence { ArtistId(it[id]) }
        .single()
    }
  }.mapError { DaoExceptionMessage(it) }

  override suspend fun getMax() = runSuspendCatching {
    db.query {
      ArtistTable
        .selects { listOf(id, artistMax) }
        .all()
        .limit(1)
        .sequence { ArtistId(it[id]) }
        .single()
    }
  }.mapError { DaoExceptionMessage(it) }

  /**
   * Throws NoSuchElementException if there is no artist name > greater than [previousArtist]
   */
  private fun Queryable.doGetPreviousArtist(previousArtist: ArtistName): ArtistIdName = ArtistTable
    .selects { listOf(id, artistName) }
    .where { artistName less previousArtist.value }
    .orderBy { artistName by Order.DESC }
    .limit(1)
    .sequence { ArtistIdName(ArtistId(it[id]), ArtistName(it[artistName])) }
    .single()

  private fun Queryable.doGetMaxArtist(): ArtistIdName = ArtistTable
    .selects { listOf(id, artistMax) }
    .all()
    .limit(1)
    .sequence { ArtistIdName(ArtistId(it[id]), ArtistName(it[artistMax])) }
    .single()

  override suspend fun getArtistName(id: ArtistId): Result<ArtistName, DaoMessage> =
    runSuspendCatching {
      db.query {
        ArtistTable
          .select { artistName }
          .where { this.id eq id.value }
          .sequence { ArtistName(it[artistName]) }
          .single()
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun getRandomArtist(): Result<ArtistIdName, DaoMessage> =
    runSuspendCatching {
      db.query {
        ArtistTable
          .selects { listOf<Column<out Any>>(id, artistName) }
          .where { id inSubQuery ArtistTable.select(id).all().orderByRandom().limit(1) }
          .sequence { ArtistIdName(ArtistId(it[id]), ArtistName(it[artistName])) }
          .single()
      }
    }.mapError { DaoExceptionMessage(it) }

  override suspend fun getRandom(): Result<ArtistId, DaoMessage> = runSuspendCatching {
      db.query {
        ArtistTable
          .select(ArtistTable.id)
          .where { id inSubQuery ArtistTable.select(id).all().orderByRandom().limit(1) }
          .longForQuery()
          .asArtistId
      }
    }.mapError { DaoExceptionMessage(it) }

}

private val songCountColumn = MediaTable.id.countDistinct()
private val songArtistAlbumCountColumn = AlbumTable.id.countDistinct()
private val albumArtistAlbumCountColumn = ArtistAlbumTable.albumId.countDistinct()
private val artistMax by lazy { ArtistTable.artistName.max().alias("artist_max_alias") }
private val artistMin by lazy { ArtistTable.artistName.min().alias("artist_min_alias") }

private val INSERT_STATEMENT = ArtistTable.insertValues {
  it[artistName].bindArg()
  it[artistSort].bindArg()
  it[artistMbid].bindArg()
  it[createdTime].bindArg()
  it[updatedTime].bindArg()
}

private val queryArtistBind = bindString()

private val QUERY_ARTIST_UPDATE_INFO = Query(
  ArtistTable
    .selects { listOf(id, artistName, artistSort, artistMbid) }
    .where { artistName eq queryArtistBind }
)

private data class ArtistUpdateInfo(
  val id: ArtistId,
  val artist: String,
  val artistSort: String,
  val artistMbid: ArtistMbid
)

private val DELETE_ARTISTS_WITH_NO_MEDIA: DeleteStatement<ArtistTable> = ArtistTable.deleteWhere {
  literal(0L) eq (
    ArtistMediaTable.select { mediaId }.where { artistId eq id } union
      MediaTable.select { id }.where {
        (artistId eq ArtistTable.id) or (albumArtistId eq ArtistTable.id)
      }
    ).selectCount().asExpression()
}

fun ArtistName.isEmpty(): Boolean = value.isEmpty()

private val BIND_ARTIST_ID: BindExpression<Long> = bindLong()
private val SELECT_NAME_FROM_BIND_ID: Expression<String> = ArtistTable
  .select(ArtistTable.artistName)
  .where { id eq BIND_ARTIST_ID }
  .limit(1)
  .asExpression()
