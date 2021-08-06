package com.netflix.spinnaker.keel.auth

enum class PermissionLevel {
  READ, WRITE;
  override fun toString() = name.toLowerCase()
}
