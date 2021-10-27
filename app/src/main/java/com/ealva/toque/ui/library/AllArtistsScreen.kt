/*
 * Copyright 2021 eAlva.com
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

package com.ealva.toque.ui.library

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.ealva.ealvabrainz.common.ArtistName
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.R
import com.ealva.toque.db.ArtistDao
import com.ealva.toque.db.ArtistDescription
import com.ealva.toque.log._e
import com.ealva.toque.persist.ArtistId
import com.ealva.toque.ui.config.LocalScreenConfig
import com.ealva.toque.ui.library.AllArtistsViewModel.ArtistInfo
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.google.accompanist.insets.navigationBarsPadding
import com.google.accompanist.insets.statusBarsPadding
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.servicesktx.add
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val LOG by lazyLogger(AllArtistsScreen::class)

enum class ArtistType {
  AlbumArtist,
  SongArtist
}

@Immutable
@Parcelize
class AllArtistsScreen(
  private val artistType: ArtistType
) : BaseLibraryItemsScreen(), KoinComponent {
  override fun bindServices(serviceBinder: ServiceBinder) {
    with(serviceBinder) { add(AllArtistsViewModel(get(), artistType)) }
  }

  @Composable
  override fun ScreenComposable(modifier: Modifier) {
    val viewModel = rememberService<AllArtistsViewModel>()
    val artists = viewModel.allArtists.collectAsState()
    AllArtistsList(artists.value)
  }
}

@Composable
private fun AllArtistsList(list: List<ArtistInfo>) {
  val listState = rememberLazyListState()
  val config = LocalScreenConfig.current
  val sheetHeight = config.getNavPlusBottomSheetHeight(isExpanded = true)

  LazyColumn(
    state = listState,
    contentPadding = PaddingValues(start = 8.dp, top = 8.dp, bottom = sheetHeight, end = 8.dp),
    modifier = Modifier
      .statusBarsPadding()
      .navigationBarsPadding(bottom = false)
  ) {
    items(list) { artistInfo -> ArtistItem(artistInfo = artistInfo) }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ArtistItem(artistInfo: ArtistInfo) {
  ListItem(
    modifier = Modifier.fillMaxWidth(),
    icon = {
      Image(
        painter = rememberImagePainter(
          data = artistInfo.artwork,
          builder = {
            error(R.drawable.ic_account_box)
            placeholder(R.drawable.ic_account_box)
          }
        ),
        contentDescription = stringResource(R.string.ArtistImage),
        modifier = Modifier.size(40.dp)
      )
    },
    text = { Text(text = artistInfo.name.value, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    secondaryText = { AlbumAndSongCount(artistInfo = artistInfo) },
  )
}

@Composable
private fun AlbumAndSongCount(artistInfo: ArtistInfo) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = LocalContext.current.resources.getQuantityString(
        R.plurals.AlbumCount,
        artistInfo.albumCount,
        artistInfo.albumCount,
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = LocalContext.current.resources.getQuantityString(
        R.plurals.SongCount,
        artistInfo.songCount,
        artistInfo.songCount,
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private interface AllArtistsViewModel {
  data class ArtistInfo(
    val artistId: ArtistId,
    val name: ArtistName,
    val artwork: Uri,
    val albumCount: Int,
    val songCount: Int
  )

  val allArtists: StateFlow<List<ArtistInfo>>

  companion object {
    operator fun invoke(
      artistDao: ArtistDao,
      artistType: ArtistType,
      dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): AllArtistsViewModel = AllAlbumArtistsViewModelImpl(artistDao, artistType, dispatcher)
  }
}

private class AllAlbumArtistsViewModelImpl(
  private val artistDao: ArtistDao,
  private val artistType: ArtistType,
  private val dispatcher: CoroutineDispatcher
) : AllArtistsViewModel, ScopedServices.Activated {
  private lateinit var scope: CoroutineScope

  override val allArtists = MutableStateFlow<List<ArtistInfo>>(emptyList())
  override fun onServiceActive() {
    scope = CoroutineScope(Job() + dispatcher)
    scope.launch {
      when (
        val result = when (artistType) {
          ArtistType.AlbumArtist -> artistDao.getAlbumArtists()
          ArtistType.SongArtist -> artistDao.getSongArtists()
        }
      ) {
        is Ok -> handleArtistList(result.value)
        is Err -> LOG._e { it("%s", result.error) }
      }
    }
  }

  private fun handleArtistList(list: List<ArtistDescription>) {
    allArtists.value = list.mapTo(ArrayList(list.size)) {
      ArtistInfo(
        artistId = it.artistId,
        name = it.name,
        artwork = it.artwork,
        albumCount = it.albumCount.toInt(),
        songCount = it.songCount.toInt()
      )
    }
  }

  override fun onServiceInactive() {
    scope.cancel()
    allArtists.value = emptyList()
  }
}