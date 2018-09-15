package com.netflix.spinnaker.orca.sql.pipeline.persistence

/**
 * An iterator that can fetch paginated data transparently to clients.
 */
internal class PagedIterator<T, C>(
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
