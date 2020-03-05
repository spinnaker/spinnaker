package com.netflix.spinnaker.keel.api

data class ResourceKind(
  val group: String,
  val kind: String,
  val version: String
) {
  init {
    require(version.matches(Regex("""\d+(\.\d+)*"""))) {
      "$version is not a valid version number"
    }
  }

  override fun toString(): String {
    return "$group/$kind@v$version"
  }

  companion object {
    private val resourceKindFormat = Regex("""([\w.-]+)/([\w.-]+)@v(.+)""")

    @JvmStatic
    fun parseKind(value: String): ResourceKind =
      resourceKindFormat
        .matchEntire(value)
        ?.destructured
        ?.let { (group, kind, version) -> ResourceKind(group, kind, version) }
        ?: error("$value is not a valid resource kind")
  }
}
