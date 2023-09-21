package com.netflix.spinnaker.keel.api

data class Moniker(
  val app: String,
  val stack: String? = null,
  val detail: String? = null,
  val sequence: Int? = null
) {
  override fun toString(): String =
    toName()

  fun toName(): String =
    when {
      stack == null && detail == null -> app
      detail == null -> "$app-$stack"
      else -> "$app-${stack.orEmpty()}-$detail"
    }

  /**
   * @return The [Moniker] with an updated [Moniker.detail] field containing as much of the specified
   * [suffix] as possible while respecting max length constraints on resource names.
   */
  fun withSuffix(suffix: String, maxNameLength: Int = 32): Moniker {
    // calculates the truncation point in the detail field based on how many characters are left of the
    // max name length after removing the current detail and accounting for empty stack and detail (which
    // cause extra dashes to be added to the name)
    var truncateAt = (maxNameLength - toName().length - suffix.length - 1)
    if (stack == null) --truncateAt
    if (detail == null) --truncateAt else truncateAt += detail!!.length
    val updatedDetail = listOfNotNull(detail?.take(truncateAt), suffix).joinToString("-")
    return copy(detail = updatedDetail)
  }
}
