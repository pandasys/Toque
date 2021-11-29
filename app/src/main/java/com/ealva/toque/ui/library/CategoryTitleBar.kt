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

package com.ealva.toque.ui.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun CategoryTitleBar(categoryItem: LibraryCategories.CategoryItem) {
  LibraryCategory(
    item = categoryItem,
    boxSize = 48.dp,
    iconSize = 38.dp,
    textStyle = MaterialTheme.typography.h6,
    padding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    textStartPadding = 10.dp
  )
}
