package com.netflix.spinnaker.cats.sql.cache

enum class SqlSchemaVersion(val version: Int) {
  V1(1),
  V2(2);

  companion object {
    fun current(): Int = V2.version
  }
}
