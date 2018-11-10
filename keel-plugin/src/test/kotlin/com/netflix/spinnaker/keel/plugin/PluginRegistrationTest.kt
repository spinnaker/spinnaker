package com.netflix.spinnaker.keel.plugin

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PluginRegistrationTest {

  val registry: PluginRegistry = mock()

  val amazonAssetPlugin = mock<AssetPlugin>()
  val registrar = PluginRegistrar(listOf(amazonAssetPlugin), registry)

  @BeforeEach
  fun setUpMockPlugin() {
    whenever(amazonAssetPlugin.name) doReturn "Amazon plugin"
    whenever(amazonAssetPlugin.supportedKinds) doReturn listOf(
      "ec2.SecurityGroup",
      "ec2.ClassicLoadBalancer"
    )
  }

  @AfterEach
  fun resetMocks() {
    reset(registry, amazonAssetPlugin)
  }

  @Test
  fun `registers plugins on startup`() {
    registrar.onDiscoveryUp()

    verify(registry).register(amazonAssetPlugin)
  }
}
