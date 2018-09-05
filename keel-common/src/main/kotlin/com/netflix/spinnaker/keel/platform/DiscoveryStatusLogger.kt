package com.netflix.spinnaker.keel.platform

import com.google.common.base.MoreObjects
import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class DiscoveryStatusLogger(
  private val instanceInfo: InstanceInfo
) : ApplicationListener<RemoteStatusChangedEvent> {

  private val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override fun onApplicationEvent(event: RemoteStatusChangedEvent) {
    event.source.also {
      when {
        it.status == InstanceInfo.InstanceStatus.UP ->
          log.info("Instance is {} : {}", it.status, instanceInfo.info)
        it.previousStatus == InstanceInfo.InstanceStatus.UP ->
          log.warn("Instance just went {}", it.status)
      }
    }
  }

  private val InstanceInfo.info: String
    get() = MoreObjects
      .toStringHelper(this)
      .add("appName", appName)
      .add("asgName", asgName)
      .add("hostName", hostName)
      .add("instanceId", instanceId)
      .add("vip", vipAddress)
      .add("status", status)
      .toString()
}
