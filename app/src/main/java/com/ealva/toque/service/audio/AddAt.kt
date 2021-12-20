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

package com.ealva.toque.service.audio

import com.ealva.toque.common.ShuffleMedia

enum class AddAt {
  AfterCurrent {
    override fun addTo(
        addTo: MutableList<PlayableAudioItem>,
        queueItems: List<PlayableAudioItem>,
        newItems: List<PlayableAudioItem>,
        shuffleMedia: ShuffleMedia,
        shuffler: ListShuffler
    ) {
      addTo.addAll(
        ArrayList<PlayableAudioItem>(newItems.size + queueItems.size).apply {
          addAll(newItems)
          addAll(queueItems)
          if (shuffleMedia.value) shuffler.shuffleInPlace(this)
        }
      )
    }
  },
  AtEnd {
    override fun addTo(
        addTo: MutableList<PlayableAudioItem>,
        queueItems: List<PlayableAudioItem>,
        newItems: List<PlayableAudioItem>,
        shuffleMedia: ShuffleMedia,
        shuffler: ListShuffler
    ) {
      addTo.addAll(
        ArrayList<PlayableAudioItem>(newItems.size + queueItems.size).apply {
          addAll(queueItems)
          addAll(newItems)
          if (shuffleMedia.value) shuffler.shuffleInPlace(this)
        }
      )
    }
  };

  abstract fun addTo(
      addTo: MutableList<PlayableAudioItem>,
      queueItems: List<PlayableAudioItem>,
      newItems: List<PlayableAudioItem>,
      shuffleMedia: ShuffleMedia,
      shuffler: ListShuffler
  )
}
