package com.netflix.spinnaker.keel.auth

enum class AuthorizationResourceType {
  ACCOUNT, APPLICATION, SERVICE_ACCOUNT, ROLE, BUILD_SERVICE;

  override fun toString() = name.toLowerCase()
}
