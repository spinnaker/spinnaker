package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetBase
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import com.netflix.spinnaker.keel.processing.randomBytes
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull

abstract class AssetRepositoryTests<T : AssetRepository> {

  abstract val subject: T

  val callback: (AssetBase) -> Unit = mock()

  @AfterEach
  fun resetMocks() {
    reset(callback)
  }

  @Test
  fun `when no assets exist rootAssets is a no-op`() {
    subject.rootAssets(callback)

    verifyZeroInteractions(callback)
  }

  @Test
  fun `when no assets exist allAssets returns an empty collection`() {
    subject.allAssets(callback)

    verifyZeroInteractions(callback)
  }

  @Test
  fun `after storing a asset with no dependencies it is returned by rootAssets`() {
    val asset = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = randomBytes()
    )

    subject.store(asset)
    subject.rootAssets(callback)

    verify(callback).invoke(asset)
  }

  @Test
  fun `after storing a asset with no dependencies it is returned by allAssets`() {
    val asset = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = randomBytes()
    )

    subject.store(asset)
    subject.allAssets(callback)

    verify(callback).invoke(asset)
  }

  @Test
  fun `after storing a asset with dependencies it is not returned by rootAssets`() {
    val asset = Asset(
      id = AssetId("LoadBalancer:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:LoadBalancer",
      dependsOn = setOf(AssetId("SecurityGroup:ec2:test:us-west-2:fnord")),
      spec = randomBytes()
    )

    subject.store(asset)
    subject.rootAssets(callback)

    verify(callback, never()).invoke(asset)
  }

  @Test
  fun `after storing a asset with dependencies it is returned by allAssets`() {
    val asset = Asset(
      id = AssetId("LoadBalancer:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:LoadBalancer",
      dependsOn = setOf(AssetId("SecurityGroup:ec2:test:us-west-2:fnord")),
      spec = randomBytes()
    )

    subject.store(asset)
    subject.allAssets(callback)

    verify(callback).invoke(asset)
  }

  @Test
  fun `after storing a partial asset it is returned by allAssets`() {
    val asset = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = randomBytes()
    )

    val partial = PartialAsset(
      id = AssetId("SecurityGroupRule:ec2:test:us-west-2:fnord:whatever"),
      root = asset.id,
      apiVersion = "1.0",
      kind = "ec2:SecurityGroupRule",
      spec = randomBytes()
    )

    subject.store(asset)
    subject.store(partial)

    subject.allAssets(callback)

    verify(callback).invoke(asset)
    verify(callback).invoke(partial)
  }

  @Test
  fun `after storing an asset it is can be retrieved by id`() {
    val asset = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = randomBytes()
    )

    subject.store(asset)

    expectThat(subject.get(asset.id)).isEqualTo(asset)
  }

  @Test
  fun `after storing an asset its state is unknown`() {
    val asset = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = randomBytes()
    )

    subject.store(asset)

    expectThat(subject.lastKnownState(asset.id))
      .isNotNull()
      .map { it.first }
      .isEqualTo(Unknown)
  }

  @Test
  fun `assets with different ids do not overwrite each other`() {
    val asset1 = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = randomBytes()
    )
    val asset2 = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-east-1:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = randomBytes()
    )

    subject.store(asset1)
    subject.store(asset2)
    subject.rootAssets(callback)

    argumentCaptor<Asset>().apply {
      verify(callback, times(2)).invoke(capture())
      expectThat(allValues)
        .hasSize(2)
        .containsExactlyInAnyOrder(asset1, asset2)
    }
  }

  @Test
  fun `storing a new version of an asset replaces the old`() {
    val asset1 = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = randomBytes()
    )
    subject.store(asset1)

    val asset2 = asset1.copy(
      spec = randomBytes()
    )
    subject.store(asset2)

    expectThat(subject.get(asset1.id))
      .isNotNull()
      .map(Asset::spec)
      .isEqualTo(asset2.spec)
  }
}
