package com.netflix.spinnaker.config

import com.netflix.spinnaker.kork.plugins.remote.RemotePluginsProvider
import com.netflix.spinnaker.orca.remote.RemoteStageExtensionPointDefinition
import com.netflix.spinnaker.orca.remote.pipeline.RemoteStage
import com.netflix.spinnaker.orca.remote.service.RemoteStageExtensionService
import com.netflix.spinnaker.orca.remote.tasks.MonitorRemoteStageTask
import com.netflix.spinnaker.orca.remote.tasks.StartRemoteStageTask
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RemoteStageConfiguration {

  @Bean
  fun remoteStageExtensionPointDefinition(): RemoteStageExtensionPointDefinition =
    RemoteStageExtensionPointDefinition()

  @Bean
  fun remoteStageExtensionService(
    remotePluginsProvider: RemotePluginsProvider,
    remoteStageExtensionPointDefinition: RemoteStageExtensionPointDefinition
  ): RemoteStageExtensionService =
    RemoteStageExtensionService(remotePluginsProvider, remoteStageExtensionPointDefinition)

  @Bean
  fun remoteStage(remoteStageExtensionService: RemoteStageExtensionService): RemoteStage =
    RemoteStage(remoteStageExtensionService)

  @Bean
  fun startRemoteStageTask(remoteStageExtensionService: RemoteStageExtensionService): StartRemoteStageTask =
    StartRemoteStageTask(remoteStageExtensionService)

  @Bean
  fun monitorRemoteStageTask(): MonitorRemoteStageTask =
    MonitorRemoteStageTask()
}
