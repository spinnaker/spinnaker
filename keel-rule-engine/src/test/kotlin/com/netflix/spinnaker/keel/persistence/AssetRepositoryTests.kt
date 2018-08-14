package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.AssetDesiredState
import com.netflix.spinnaker.keel.model.AssetId
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

abstract class AssetRepositoryTests<T : AssetRepository> {

  abstract val subject: T

  val callback: (AssetDesiredState) -> Unit = mock()

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
    val asset = AssetDesiredState(
      id = AssetId("SecurityGroup:ec2:test:us-west-2:fnord"),
      apiVersion = "1.0",
      kind = "ec2:SecurityGroup",
      payload = ByteArray(0)
    )

    subject.store(asset)
    subject.assets(callback)

    verify(callback).invoke(asset)
  }

}
