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

package com.ealva.toque.file

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Date
import java.util.concurrent.TimeUnit

inline class AudioContentId(val value: Long)
inline class VideoContentId(val value: Long)

/**
 * MediaStorage provides an interface to read and write the device MediaStore.
 */
interface MediaStorage {
  suspend fun location(id: AudioContentId): Uri

  /**
   * Get all audio grouped by artist and album. Each list of the flow contains all the tracks
   * found for a given artist.album. eg. a list might contain all the songs on the Beatles Let It Be
   * album and another list would contain all the songs on the Beatles Revolver album. This
   * grouping is based on the MediaStore database.
   */
  fun audioFlow(): Flow<List<AudioInfo>>

  companion object {
    operator fun invoke(context: Context): MediaStorage = MediaStorageImpl(context)
  }
}

/**
 * Unlikely to be this many tracks on an album so use this as starting list size.
 */
private const val DEFAULT_AUDIO_LIST_SIZE = 64

private class MediaStorageImpl(context: Context) : MediaStorage {
  private val resolver = context.contentResolver

  private val audioCollection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
  } else {
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  }

//  @RequiresApi(Build.VERSION_CODES.R)
//  private val versionRAudioFields = listOf(
//    MediaStore.Audio.AudioColumns.ALBUM_ARTIST,
//    MediaStore.Audio.AudioColumns.COMPOSER,
//    MediaStore.MediaColumns.DISC_NUMBER,
//    MediaStore.MediaColumns.DURATION,
//    MediaStore.Audio.AudioColumns.GENRE,
//    MediaStore.MediaColumns.NUM_TRACKS,
//  )
//
// MediaStore.Audio.AudioColumns.IS_ALARM,
// MediaStore.Audio.AudioColumns.IS_MUSIC,
// MediaStore.Audio.AudioColumns.IS_NOTIFICATION,
// MediaStore.Audio.AudioColumns.IS_RINGTONE,
// MediaStore.Audio.AudioColumns.IS_PODCAST
// MediaStore.Audio.AudioColumns.IS_AUDIOBOOK API 29

  @Suppress("DEPRECATION")
  private val audioQueryFields = arrayOf(
    MediaStore.Audio.Media._ID,
    MediaStore.Audio.AlbumColumns.ALBUM,
    MediaStore.MediaColumns.DATA,
    MediaStore.Audio.AudioColumns.TITLE,
//    MediaStore.Audio.AlbumColumns.ALBUM,
//    MediaStore.Audio.ArtistColumns.ARTIST,
    MediaStore.Audio.AudioColumns.DATE_ADDED,
    MediaStore.Audio.AudioColumns.DATE_MODIFIED,
    MediaStore.Audio.AudioColumns.MIME_TYPE,
    MediaStore.Audio.AudioColumns.SIZE,
//    MediaStore.Audio.AudioColumns.TRACK,
//    MediaStore.Audio.AudioColumns.YEAR
  )

  override suspend fun location(id: AudioContentId): Uri =
    ContentUris.withAppendedId(audioCollection, id.value)

  fun Cursor.longColumnToDate(columnIndex: Int): Date =
    Date(TimeUnit.SECONDS.toMillis(getLong(columnIndex)))

  override fun audioFlow(): Flow<List<AudioInfo>> = flow {
    resolver.query(
      audioCollection,
      audioQueryFields,
      null,
      null,
      MediaStore.Audio.ArtistColumns.ARTIST + ", " + MediaStore.Audio.AlbumColumns.ALBUM
    )?.use { c: Cursor ->
      val iId = c.indexOf(MediaStore.Audio.Media._ID)
      val iAlbum = c.indexOf(MediaStore.Audio.AlbumColumns.ALBUM)
      @Suppress("DEPRECATION") val iData = c.indexOf(MediaStore.MediaColumns.DATA)
      val iTitle = c.indexOf(MediaStore.Audio.AudioColumns.TITLE)
      val iDateAdded = c.indexOf(MediaStore.Audio.AudioColumns.DATE_ADDED)
      val iDateModified = c.indexOf(MediaStore.Audio.AudioColumns.DATE_MODIFIED)
      val iMimeType = c.indexOf(MediaStore.Audio.AudioColumns.MIME_TYPE)
      val iSize = c.indexOf(MediaStore.Audio.AudioColumns.SIZE)

      val list = ArrayList<AudioInfo>(DEFAULT_AUDIO_LIST_SIZE)
      var currentAlbum = ""
      while (c.moveToNext()) {
        val nextAlbum = c.getString(iAlbum)
        if (nextAlbum != currentAlbum) {
          if (list.isNotEmpty()) emit(list.toList())
          list.clear()
          currentAlbum = nextAlbum
        }
        val id = AudioContentId(c.getLong(iId))
        list += AudioInfo(
          id,
          location(id),
          c.getString(iData),
          c.getString(iTitle),
          c.longColumnToDate(iDateAdded),
          c.longColumnToDate(iDateModified),
          c.getString(iMimeType),
          c.getLong(iSize)
        )
      }
      if (list.isNotEmpty()) emit(list.toList())
    }
  }
}

private fun Cursor.indexOf(columnName: String): Int = getColumnIndexOrThrow(columnName)

data class AudioInfo(
  val id: AudioContentId,
  val location: Uri,
  val path: String,
  val title: String,
  val dateAdded: Date,
  val dateModified: Date,
  val mimeType: String,
  val size: Long
)

fun Context.runMediaScanner(
  paths: List<String>,
  completed: MediaScannerConnection.OnScanCompletedListener
) {
  MediaScannerConnection.scanFile(
    this,
    paths.toTypedArray(),
    null,
    completed
  )
}
