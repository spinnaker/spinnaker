package com.netflix.spinnaker.keel.model

/**
 * Types that are common between CLoudDriver and Keel representations of ELBs.
 */

enum class Scheme {
  internal, external
}

data class Listener(
  val protocol: Protocol,
  val loadBalancerPort: Int,
  val instanceProtocol: Protocol,
  val instancePort: Int
)

enum class Protocol {
  HTTP, HTTPS, TCP, SSL
}
