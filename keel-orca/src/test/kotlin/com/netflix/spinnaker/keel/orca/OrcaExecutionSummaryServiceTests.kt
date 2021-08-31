package com.netflix.spinnaker.keel.orca

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty

class OrcaExecutionSummaryServiceTests {
  val mapper = configuredTestObjectMapper()
  val orcaService: OrcaService = mockk()

  val subject = OrcaExecutionSummaryService(
    orcaService,
    mapper
  )

  @Test
  fun `can read managed rollout stage`() {
    val response = javaClass.getResource("/managed-rollout-execution.json").readText()
    coEvery { orcaService.getOrchestrationExecution(any()) } returns mapper.readValue(response)

    val summary = runBlocking {
      subject.getSummary("1")
    }

    expectThat(summary.summaryText).isNotEmpty()
    expectThat(summary.summaryText).contains("2/2")
    expectThat(summary.status).isEqualTo(TaskStatus.SUCCEEDED)
  }

  @Test
  fun `can read a single region deploy stage`() {
    val response = javaClass.getResource("/single-region-deploy.json").readText()
    coEvery { orcaService.getOrchestrationExecution(any()) } returns mapper.readValue(response)

    val summary = runBlocking {
      subject.getSummary("1")
    }

    expectThat(summary.summaryText).isNotEmpty()
    expectThat(summary.summaryText).isEqualTo(summary.name)
    expectThat(summary.status).isEqualTo(TaskStatus.SUCCEEDED)
  }

  @Test
  fun `can read a running single region deploy stage`() {
    val response = javaClass.getResource("/running-single-region-deploy.json").readText()
    coEvery { orcaService.getOrchestrationExecution(any()) } returns mapper.readValue(response)

    val summary = runBlocking {
      subject.getSummary("1")
    }

    expectThat(summary.summaryText).isNotEmpty()
    expectThat(summary.summaryText).isEqualTo(summary.name)
    expectThat(summary.status).isEqualTo(TaskStatus.RUNNING)
  }
}
