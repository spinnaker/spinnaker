package com.netflix.spinnaker.keel.eureka

import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.keel.info.InstanceIdSupplier

class EurekaInstanceIdSupplier(private val instanceInfo: InstanceInfo) : InstanceIdSupplier {
  override fun get(): String = instanceInfo.instanceId
}
