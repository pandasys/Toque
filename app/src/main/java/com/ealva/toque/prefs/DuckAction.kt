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

package com.ealva.toque.prefs

import com.ealva.toque.R
import com.ealva.toque.persist.HasConstId
import com.ealva.toque.persist.reify
import com.ealva.toque.res.HasDescription

@Suppress("NOTHING_TO_INLINE")
inline fun Int.toDuckAction(): DuckAction = DuckAction::class.reify(this, DuckAction.DEFAULT)

enum class DuckAction(
  override val id: Int,
  override val stringRes: Int
) : HasConstId, HasDescription {
  Duck(1, R.string.Duck),
  Pause(2, R.string.Pause),
  DoNothing(3, R.string.DoNothing);

  companion object {
    val DEFAULT = Duck
  }
}
