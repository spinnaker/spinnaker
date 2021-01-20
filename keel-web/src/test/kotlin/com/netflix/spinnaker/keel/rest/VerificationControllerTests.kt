package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.rest.VerificationController.RetryVerificationRequest
import com.netflix.spinnaker.keel.rest.VerificationController.UpdateVerificationStatusRequest
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE
import org.junit.jupiter.params.provider.NullSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant.now
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

@SpringBootTest(webEnvironment = MOCK)
@AutoConfigureMockMvc
internal class VerificationControllerTests
@Autowired constructor(
  private val mvc: MockMvc,
  private val jsonMapper: ObjectMapper
) {
  private data class DummyVerification(val value: String) : Verification {
    override val type = "dummy"
    override val id: String = "$type:$value"
  }

  @MockkBean
  lateinit var deliveryConfigRepository: DeliveryConfigRepository

  @MockkBean(relaxUnitFun = true)
  lateinit var verificationRepository: VerificationRepository

  private val verification = DummyVerification("1")
  private val environment = Environment(
    name = "test",
    verifyWith = listOf(verification)
  )
  private val artifact = DockerArtifact(
    name = "fnord",
    deliveryConfigName = "fnord-manifest",
    reference = "fnord-docker"
  )
  private val deliveryConfig = DeliveryConfig(
    application = "fnord",
    name = "fnord-manifest",
    serviceAccount = "jamm@illuminati.org",
    artifacts = setOf(artifact),
    environments = setOf(environment)
  )

  @BeforeEach
  fun setup() {
    every {
      deliveryConfigRepository.getByApplication(deliveryConfig.application)
    } returns deliveryConfig
  }

  @ParameterizedTest(name = "verification status can be overridden with {0}")
  @EnumSource(ConstraintStatus::class, names = ["OVERRIDE_PASS", "OVERRIDE_FAIL"])
  fun `verification status can be overridden by user request`(status: ConstraintStatus) {
    val payload = UpdateVerificationStatusRequest(
      verificationId = verification.id,
      artifactReference = artifact.reference,
      artifactVersion = "1.0.0",
      status = status,
      comment = "I swear this is fine"
    )
    val user = "fzlem@netflix.com"
    val request = post("/${deliveryConfig.application}/environment/test/verifications")
      .header("X-SPINNAKER-USER", user)
      .contentType(APPLICATION_JSON)
      .accept(APPLICATION_JSON)
      .content(jsonMapper.writeValueAsString(payload))

    mvc.perform(request).andExpect(status().isOk)

    verify {
      verificationRepository.updateState(
        match {
          it.deliveryConfig == deliveryConfig
        },
        verification,
        payload.status,
        mapOf("overriddenBy" to user, "comment" to payload.comment)
      )
    }
  }

  @ParameterizedTest(name = "verification cannot be overridden with {0}")
  @EnumSource(ConstraintStatus::class, names = ["OVERRIDE_PASS", "OVERRIDE_FAIL"], mode = EXCLUDE)
  fun `only override statuses are accepted`(status: ConstraintStatus) {
    val payload = UpdateVerificationStatusRequest(
      verificationId = verification.id,
      artifactReference = artifact.reference,
      artifactVersion = "1.0.0",
      status = status,
      comment = "I swear this is fine"
    )
    val user = "fzlem@netflix.com"
    val request = post("/${deliveryConfig.application}/environment/test/verifications")
      .header("X-SPINNAKER-USER", user)
      .contentType(APPLICATION_JSON)
      .accept(APPLICATION_JSON)
      .content(jsonMapper.writeValueAsString(payload))

    mvc.perform(request).andExpect(status().isUnprocessableEntity)

    verify(exactly = 0) {
      verificationRepository.updateState(any(), any(), any(), any())
    }
  }

  @ParameterizedTest(name = "verification can be retried when the current status is {0}")
  @EnumSource(ConstraintStatus::class, names = ["NOT_EVALUATED", "PENDING"], mode = EXCLUDE)
  @NullSource
  fun `verification can be retried if not currently running`(currentStatus: ConstraintStatus?) {
    val payload = RetryVerificationRequest(
      verificationId = verification.id,
      artifactReference = artifact.reference,
      artifactVersion = "1.0.0"
    )
    val user = "fzlem@netflix.com"
    val request = post("/${deliveryConfig.application}/environment/test/verifications/retry")
      .header("X-SPINNAKER-USER", user)
      .contentType(APPLICATION_JSON)
      .accept(APPLICATION_JSON)
      .content(jsonMapper.writeValueAsString(payload))

    every {
      verificationRepository.getState(any(), any())
    } returns currentStatus?.let { VerificationState(it, now(), now()) }

    mvc.perform(request).andExpect(status().isOk)

    verify {
      verificationRepository.updateState(
        match {
          it.deliveryConfig == deliveryConfig
        },
        verification,
        NOT_EVALUATED,
        mapOf("retryRequestedBy" to user)
      )
    }
  }

  @ParameterizedTest(name = "verification cannot be retried when the current status is {0}")
  @EnumSource(ConstraintStatus::class, names = ["NOT_EVALUATED", "PENDING"])
  fun `verification cannot be retried if running`(currentStatus: ConstraintStatus) {
    val payload = RetryVerificationRequest(
      verificationId = verification.id,
      artifactReference = artifact.reference,
      artifactVersion = "1.0.0"
    )
    val user = "fzlem@netflix.com"
    val request = post("/${deliveryConfig.application}/environment/test/verifications/retry")
      .header("X-SPINNAKER-USER", user)
      .contentType(APPLICATION_JSON)
      .accept(APPLICATION_JSON)
      .content(jsonMapper.writeValueAsString(payload))

    every {
      verificationRepository.getState(any(), any())
    } returns VerificationState(currentStatus, now(), null)

    mvc.perform(request).andExpect(status().isConflict)

    verify(exactly = 0) {
      verificationRepository.updateState(any(), any(), any(), any())
    }
  }
}
