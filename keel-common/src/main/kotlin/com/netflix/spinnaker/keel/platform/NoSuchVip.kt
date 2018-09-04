package com.netflix.spinnaker.keel.platform

/**
 * Re-package the raw `RuntimeException` Eureka throws.
 */
class NoSuchVip(vip: String, cause: Throwable? = null) :
  RuntimeException("VIP $vip not found in Eureka", cause)
