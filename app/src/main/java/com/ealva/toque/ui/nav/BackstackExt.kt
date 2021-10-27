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

package com.ealva.toque.ui.nav

import com.ealva.toque.navigation.ComposeKey
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.StateChange

fun Backstack.goToAboveRoot(screen: ComposeKey) =
  setHistory(listOf(root(), screen), StateChange.REPLACE)