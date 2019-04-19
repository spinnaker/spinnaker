package com.netflix.spinnaker.cats.sql.cache

enum class SqlSchemaVersion(val version: Int) {
  V1(1);

  companion object {
      fun current(): Int = V1.version
  }
}
