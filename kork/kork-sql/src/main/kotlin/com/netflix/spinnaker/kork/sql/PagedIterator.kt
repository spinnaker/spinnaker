/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.sql

/**
 * An iterator that can fetch paginated data transparently to clients.
 */
class PagedIterator<T, C>(
  private val pageSize: Int,
  private val toCursor: (T) -> C,
  private val nextPage: (Int, C?) -> Iterable<T>
) : Iterator<T> {
  override fun hasNext(): Boolean =
    if (isLastPage && index > currentPage.lastIndex) {
      false
    } else {
      loadNextChunkIfNecessary()
      index <= currentPage.lastIndex
    }

  override fun next(): T =
    if (isLastPage && index > currentPage.lastIndex) {
      throw NoSuchElementException()
    } else {
      loadNextChunkIfNecessary()
      currentPage[index++]
    }

  private fun loadNextChunkIfNecessary() {
    if (index !in currentPage.indices) {
      currentPage.clear()
      nextPage(pageSize, cursor).let(currentPage::addAll)
      index = 0
      cursor = currentPage.lastOrNull()?.let(toCursor)
      isLastPage = currentPage.size < pageSize
    }
  }

  private var cursor: C? = null
  private val currentPage: MutableList<T> = mutableListOf()
  private var index: Int = -1
  private var isLastPage: Boolean = false
}
