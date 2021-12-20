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

package com.ealva.toque.db.smart

import com.ealva.toque.R
import com.ealva.toque.common.Rating
import com.ealva.toque.common.fetch
import com.ealva.toque.common.toRating
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.between
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.less
import com.ealva.welite.db.expr.neq
import com.ealva.welite.db.table.Column
import kotlinx.parcelize.Parcelize
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.toque.log._e

private val LOG by lazyLogger(RatingMatcher::class)

@Parcelize
enum class RatingMatcher(
  override val id: Int,
  private val stringRes: Int
) : Matcher<Int> {
  Is(1, R.string.is_) {
    override fun makeClause(column: Column<Int>, first: Long, second: Long): Op<Boolean> =
      column eq first.toInt()
  },
  IsNot(2, R.string.is_not) {
    override fun makeClause(column: Column<Int>, first: Long, second: Long): Op<Boolean> =
      column neq first.toInt()
  },
  IsGreaterThan(3, R.string.is_greater_than) {
    override fun makeClause(column: Column<Int>, first: Long, second: Long): Op<Boolean> =
      column greater first.toInt()
  },
  IsLessThan(4, R.string.is_less_than) {
    override fun makeClause(column: Column<Int>, first: Long, second: Long): Op<Boolean> =
      column less first.toInt()
  },
  IsInTheRange(5, R.string.is_in_the_range) {
    override fun makeClause(column: Column<Int>, first: Long, second: Long): Op<Boolean> =
      column.between(first.toInt(), second.toInt())

    override fun willAccept(data: MatcherData): Boolean =
      data.first >= -1 && data.first <= 100 && data.first < data.second && data.second <= 100

    override fun sanitizeData(previous: MatcherData?, data: MatcherData): MatcherData {
      LOG._e { it("data:%s", data) }
      var first: Int = data.first.toInt().coerceAtLeast(Rating.RATING_0.value)
      var second: Int = data.second.toInt().coerceIn(first, Rating.RATING_5.value)
      if (first >= second) {
          when {
            first == Rating.RATING_5.value -> {
              first = Rating.RATING_4_5.value
              second = Rating.RATING_5.value
            }
            second == Rating.RATING_0.value -> {
              first = Rating.RATING_0.value
              second = Rating.RATING_0_5.value
            }
            else -> {
              second = first + Rating.RATING_0_5.value
            }
          }
      }
      return data.copy(first = first.toLong(), second = second.toLong())
    }
  };

  override fun makeWhereClause(column: Column<Int>, data: MatcherData): Op<Boolean> {
    return makeClause(column, data.first, data.second)
  }

  protected abstract fun makeClause(
    column: Column<Int>,
    first: Long,
    second: Long
  ): Op<Boolean>

  override fun willAccept(data: MatcherData): Boolean {
    return data.first >= -1 && data.first <= 100
  }

  open fun sanitizeData(previous: MatcherData?, data: MatcherData): MatcherData = data.copy(
    first = data.first.toInt().toRating().coerceIn(Rating.VALID_RANGE).value.toLong()
  )

  override fun toString(): String {
    return fetch(stringRes)
  }

  companion object {
    val allValues: List<RatingMatcher> = values().toList()

    fun fromId(matcherId: Int): RatingMatcher {
      return allValues.find { it.id == matcherId }
        ?: throw IllegalArgumentException("No matcher with id=$matcherId")
    }
  }
}
