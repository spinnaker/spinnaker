package com.netflix.spinnaker.orca.remote.service

import com.netflix.spinnaker.kork.plugins.remote.RemotePluginsProvider
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtension
import com.netflix.spinnaker.orca.remote.RemoteStageExtensionPointDefinition
import com.netflix.spinnaker.orca.remote.model.RemoteStageExtensionPointConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class RemoteStageExtensionServiceTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("Gets the remote extension for the selected stage type") {
      every { remotePluginsProvider.getExtensionsByType(remoteStageExtensionPointDefinition.type()) } returns listOf(
        remoteWaitStage1, remoteDeployStage
      )

      val result = subject.getByStageType("remoteWait")
      expectThat(result).isA<RemoteExtension>()
        .get { result.pluginId }.isEqualTo("netflix.remote1")
    }

    test("Throws DuplicateRemoteStageTypeException on duplicate stage type") {
      every { remotePluginsProvider.getExtensionsByType(remoteStageExtensionPointDefinition.type()) } returns listOf(
        remoteWaitStage1, remoteWaitStage2
      )
      expectThrows<DuplicateRemoteStageTypeException> { subject.getByStageType("remoteWait") }
    }

    test("Throws RemoteStageTypeNotFoundException") {
      every { remotePluginsProvider.getExtensionsByType(remoteStageExtensionPointDefinition.type()) } returns listOf(
        remoteWaitStage1, remoteWaitStage2
      )
      expectThrows<RemoteStageTypeNotFoundException> { subject.getByStageType("notFoundRemoteStage") }
    }
  }

  private class Fixture {
    val remotePluginsProvider: RemotePluginsProvider = mockk(relaxed = true)
    val remoteStageExtensionPointDefinition: RemoteStageExtensionPointDefinition = RemoteStageExtensionPointDefinition()
    val subject = RemoteStageExtensionService(remotePluginsProvider, remoteStageExtensionPointDefinition)

    val remoteWaitStage1 = RemoteExtension(
      "remote-wait-stage-extension",
      "netflix.remote1",
      "stage",
      RemoteStageExtensionPointConfig(
        type = "remoteWait",
        description = "Waits on a thing",
        label = "A remote wait stage",
        parameters = mutableMapOf()
      ),
      mockk(relaxed = true)
    )
    val remoteWaitStage2 = RemoteExtension(
      "remote-wait-stage-extension",
      "netflix.remote2",
      "stage",
      RemoteStageExtensionPointConfig(
        type = "remoteWait",
        description = "Waits on a thing",
        label = "A remote wait stage",
        parameters = mutableMapOf()
      ),
      mockk(relaxed = true)
    )
    val remoteDeployStage = RemoteExtension(
      "remote-deploy-stage-extension",
      "netflix.remote2",
      "stage",
      RemoteStageExtensionPointConfig(
        type = "remoteDeploy",
        description = "Deploys a thing",
        label = "A remote deploy stage",
        parameters = mutableMapOf()
      ),
      mockk(relaxed = true)
    )
  }
}
