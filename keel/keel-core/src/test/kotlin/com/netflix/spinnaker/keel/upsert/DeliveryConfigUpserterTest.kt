package com.netflix.spinnaker.keel.upsert

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.PersistenceRetryConfig
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.exceptions.ValidationException
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.PersistenceRetry
import com.netflix.spinnaker.keel.test.deliveryArtifact
import com.netflix.spinnaker.keel.test.submittedDeliveryConfig
import com.netflix.spinnaker.keel.validators.DeliveryConfigValidator
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import strikt.api.expectThrows

internal class DeliveryConfigUpserterTest {
  private val repository: KeelRepository = mockk()
  private val mapper: ObjectMapper = mockk()
  private val validator: DeliveryConfigValidator = mockk()
  private val publisher: ApplicationEventPublisher = mockk()
  private val springEnv: Environment = mockk()
  private val persistenceRetry = PersistenceRetry(PersistenceRetryConfig())

  private val subject = DeliveryConfigUpserter(
    repository = repository,
    mapper = mapper,
    validator = validator,
    publisher = publisher,
    springEnv = springEnv,
    persistenceRetry = persistenceRetry
  )

  private val submittedDeliveryConfig = submittedDeliveryConfig()
  private val deliveryConfig = submittedDeliveryConfig.toDeliveryConfig()

  @BeforeEach
  fun setupMocks() {
    every {
      repository.getDeliveryConfigForApplication(any())
    } returns deliveryConfig

    every {
      validator.validate(any())
    } just Runs

    every {
      repository.upsertDeliveryConfig(submittedDeliveryConfig)
    } returns deliveryConfig

    every {
      springEnv.getProperty("keel.notifications.send-config-changed", Boolean::class.java, true)
    } returns true
  }

  @Test
  fun `no upsert if validation fails`() {
    every {
      validator.validate(any())
    }.throws(ValidationException("bad config"))

    expectThrows<ValidationException> {
      subject.upsertConfig(submittedDeliveryConfig)
    }
    verify(exactly = 0) { repository.upsertDeliveryConfig(any<SubmittedDeliveryConfig>()) }
  }

  @Test
  fun `can upsert a valid delivery config`() {
    subject.upsertConfig(submittedDeliveryConfig)
    verify { repository.upsertDeliveryConfig(submittedDeliveryConfig) }
    verify(exactly = 0) { publisher.publishEvent(any<Object>()) } // No diff
  }

  @Test
  fun `notify on config changes`() {
    every {
      publisher.publishEvent(any<Object>())
    } just Runs

    every {
      repository.getDeliveryConfigForApplication(any())
    } returns deliveryConfig.copy(artifacts = setOf(deliveryArtifact(name = "differentArtifact")))

    subject.upsertConfig(submittedDeliveryConfig)

    verify { repository.upsertDeliveryConfig(submittedDeliveryConfig) }
    verify { publisher.publishEvent(any<Object>()) }
  }
}
