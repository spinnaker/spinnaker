package com.netflix.spinnaker.keel.eureka

import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import java.net.InetAddress

object LocalInstanceIdSupplier : InstanceIdSupplier {
  override fun get(): String = InetAddress.getLocalHost().hostName
}
