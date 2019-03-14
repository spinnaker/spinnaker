package com.netflix.spinnaker.keel.info

import java.net.InetAddress

object LocalInstanceIdSupplier : InstanceIdSupplier {
  override fun get(): String = InetAddress.getLocalHost().hostName
}
