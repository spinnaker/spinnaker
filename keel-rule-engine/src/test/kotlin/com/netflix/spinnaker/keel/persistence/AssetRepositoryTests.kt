package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEqualTo

abstract class AssetRepositoryTests<T : AssetRepository> {

  abstract val subject: T

  val callback: (Asset) -> Unit = mock()

  @AfterEach
  fun resetMocks() {
    reset(callback)
  }

  @Test
  fun `when no assets exist assets is a no-op`() {
    subject.assets(callback)

    verifyZeroInteractions(callback)
  }

  @Test
  fun `after storing an asset it is returned by assets`() {
    val asset = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = ByteArray(0)
    )

    subject.store(asset)
    subject.assets(callback)

    verify(callback).invoke(asset)
  }

  @Test
  fun `assets with different ids do not overwrite each other`() {
    val asset1 = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = ByteArray(0)
    )
    val asset2 = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-east-1:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = ByteArray(0)
    )

    subject.store(asset1)
    subject.store(asset2)
    subject.assets(callback)

    argumentCaptor<Asset>().apply {
      verify(callback, times(2)).invoke(capture())
      expect(allValues) {
        map { size }.isEqualTo(2)
        containsExactlyInAnyOrder(asset1, asset2)
      }
    }
  }

  @Test
  fun `storing a new version of an asset replaces the old`() {
    val asset1 = Asset(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      spec = ByteArray(0)
    )
    subject.store(asset1)

    val asset2 = asset1.copy(
      spec = ByteArray(1024)
    )
    subject.store(asset2)

    subject.assets(callback)

    verify(callback).invoke(check {
      expect(it.spec).isEqualTo(asset2.spec)
    })
  }
}
