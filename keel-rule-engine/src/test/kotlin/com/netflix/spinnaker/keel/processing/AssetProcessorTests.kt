package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.persistence.InMemoryAssetRepository
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.System.nanoTime
import java.util.*

internal class AssetProcessorTests {

  val repository = InMemoryAssetRepository()
  val assetService: AssetService = mock()
  val vetoService: VetoService = mock()
  val subject = AssetProcessor(repository, assetService, vetoService)

  val id = AssetId("SecurityGroup:aws:prod:us-west-2:keel")
  val desired = Asset(
    id = id,
    kind = "SecurityGroup",
    spec = randomBytes()
  )

  @BeforeEach
  fun persistAsset() {
    repository.store(desired)
  }

  @Test
  fun `no action is taken if the current state matches the desired state`() {
    whenever(assetService.current(desired)) doReturn desired

    subject.checkAsset(desired.id)

    verify(assetService, never()).converge(any())
  }

  @Test
  fun `no action is taken if something vetoes it`() {
    whenever(assetService.current(desired)) doReturn desired.copy(spec = randomBytes())
    whenever(vetoService.allow(desired)) doReturn false

    subject.checkAsset(desired.id)

    verify(assetService, never()).converge(any())
  }

  @Test
  fun `asset gets converged if its current state differs from the desired`() {
    whenever(assetService.current(desired)) doReturn desired.copy(spec = randomBytes())
    whenever(vetoService.allow(desired)) doReturn true

    subject.checkAsset(desired.id)

    verify(assetService).converge(any())
  }
}

fun randomBytes(length: Int = 20) =
  ByteArray(length).also(Random(nanoTime())::nextBytes)
