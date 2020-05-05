package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.persistence.CombinedRepository
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity
import com.netflix.spinnaker.keel.test.locatableResource
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isFalse
import strikt.assertions.isSuccess
import strikt.assertions.isTrue

internal class AuthorizationSupportTests : JUnit5Minutests {
  private val dynamicConfigService: DynamicConfigService = mockk(relaxed = true)
  private val permissionEvaluator: FiatPermissionEvaluator = mockk(relaxed = true)
  private val combinedRepository: CombinedRepository = mockk(relaxed = true)
  private val resource = locatableResource()
  private val deliveryConfig = DeliveryConfig(
    name = "manifest",
    application = ApplicationControllerTests.application,
    serviceAccount = "keel@spinnaker",
    artifacts = emptySet(),
    environments = setOf(Environment("test", setOf(resource)))
  )
  private val application = deliveryConfig.application

  fun tests() = rootContext<AuthorizationSupport> {
    fixture {
      AuthorizationSupport(permissionEvaluator, combinedRepository, dynamicConfigService)
    }

    after {
      clearAllMocks()
    }

    context("authorization is enabled") {
      before {
        mockkStatic("org.springframework.security.core.context.SecurityContextHolder")

        every {
          SecurityContextHolder.getContext()
        } returns mockk(relaxed = true)

        every {
          dynamicConfigService.isEnabled("keel.authorization", true)
        } returns true

        every {
          combinedRepository.getResource(resource.id)
        } returns resource

        every {
          combinedRepository.getDeliveryConfig(deliveryConfig.name)
        } returns deliveryConfig

        every {
          combinedRepository.getDeliveryConfigForApplication(application)
        } returns deliveryConfig
      }

      listOf(Action.READ, Action.WRITE).forEach { action ->
        context("user has no ${action.name} access to application") {
          before {
            every {
              permissionEvaluator.hasPermission(any() as Authentication, application, "APPLICATION", action.name)
            } returns false
          }

          test("permission check specifying application name fails") {
            expectThrows<AccessDeniedException> {
              checkApplicationPermission(action, TargetEntity.APPLICATION, application)
            }
            expectThat(
              hasApplicationPermission(action.name, TargetEntity.APPLICATION.name, application)
            ).isFalse()
          }

          test("permission check specifying resource id fails") {
            expectThrows<AccessDeniedException> {
              checkApplicationPermission(action, TargetEntity.RESOURCE, resource.id)
            }
            expectThat(
              hasApplicationPermission(action.name, TargetEntity.RESOURCE.name, resource.id)
            ).isFalse()
          }

          test("permission check specifying delivery config name fails") {
            expectThrows<AccessDeniedException> {
              checkApplicationPermission(action, TargetEntity.DELIVERY_CONFIG, deliveryConfig.name)
            }
            expectThat(
              hasApplicationPermission(action.name, TargetEntity.DELIVERY_CONFIG.name, deliveryConfig.name)
            ).isFalse()
          }
        }
      }

      listOf(Action.READ, Action.WRITE).forEach { action ->
        context("user has no ${action.name} access to cloud account") {
          before {
            every {
              permissionEvaluator.hasPermission(any() as Authentication, any(), "ACCOUNT", action.name)
            } returns false
          }

          test("permission check specifying application name fails") {
            expectThrows<AccessDeniedException> {
              checkCloudAccountPermission(action, TargetEntity.APPLICATION, application)
            }
            expectThat(
              hasCloudAccountPermission(action.name, TargetEntity.APPLICATION.name, application)
            ).isFalse()
          }

          test("permission check specifying resource id fails") {
            expectThrows<AccessDeniedException> {
              checkCloudAccountPermission(action, TargetEntity.RESOURCE, resource.id)
            }
            expectThat(
              hasCloudAccountPermission(action.name, TargetEntity.RESOURCE.name, resource.id)
            ).isFalse()
          }

          test("permission check specifying delivery config name fails") {
            expectThrows<AccessDeniedException> {
              checkCloudAccountPermission(action, TargetEntity.DELIVERY_CONFIG, deliveryConfig.name)
            }
            expectThat(
              hasCloudAccountPermission(action.name, TargetEntity.DELIVERY_CONFIG.name, deliveryConfig.name)
            ).isFalse()
          }
        }
      }

      context("user has no access to service account") {
        before {
          every {
            permissionEvaluator.hasPermission(any() as Authentication, any(), "SERVICE_ACCOUNT", any())
          } returns false
        }

        test("permission check specifying application name fails") {
          expectThrows<AccessDeniedException> {
            checkServiceAccountAccess(TargetEntity.APPLICATION, application)
          }
          expectThat(
            hasServiceAccountAccess(TargetEntity.APPLICATION.name, application)
          ).isFalse()
        }

        test("permission check specifying resource id fails") {
          expectThrows<AccessDeniedException> {
            checkServiceAccountAccess(TargetEntity.RESOURCE, resource.id)
          }
          expectThat(
            hasServiceAccountAccess(TargetEntity.RESOURCE.name, resource.id)
          ).isFalse()
        }

        test("permission check specifying delivery config name fails") {
          expectThrows<AccessDeniedException> {
            checkServiceAccountAccess(TargetEntity.DELIVERY_CONFIG, deliveryConfig.name)
          }
          expectThat(
            hasServiceAccountAccess(TargetEntity.DELIVERY_CONFIG.name, deliveryConfig.name)
          ).isFalse()
        }
      }
    }

    context("authorization is disabled") {
      before {
        every {
          dynamicConfigService.isEnabled("keel.authorization", true)
        } returns false
      }

      test("all application access is allowed") {
        expectCatching {
          checkApplicationPermission(Action.READ, TargetEntity.APPLICATION, application)
          checkApplicationPermission(Action.WRITE, TargetEntity.APPLICATION, application)
        }.isSuccess()
        expectThat(
          hasApplicationPermission(Action.READ.name, TargetEntity.APPLICATION.name, application)
        ).isTrue()
        expectThat(
          hasApplicationPermission(Action.WRITE.name, TargetEntity.APPLICATION.name, application)
        ).isTrue()
      }

      test("all cloud account access is allowed") {
        expectCatching {
          checkCloudAccountPermission(Action.READ, TargetEntity.APPLICATION, application)
          checkCloudAccountPermission(Action.WRITE, TargetEntity.APPLICATION, application)
        }.isSuccess()
        expectThat(
          hasCloudAccountPermission(Action.READ.name, TargetEntity.APPLICATION.name, application)
        ).isTrue()
        expectThat(
          hasCloudAccountPermission(Action.WRITE.name, TargetEntity.APPLICATION.name, application)
        ).isTrue()
      }

      test("all service account access is allowed") {
        expectCatching {
          checkServiceAccountAccess(TargetEntity.APPLICATION, application)
          checkServiceAccountAccess(TargetEntity.APPLICATION, application)
        }.isSuccess()
        expectThat(
          hasServiceAccountAccess(TargetEntity.APPLICATION.name, application)
        ).isTrue()
        expectThat(
          hasServiceAccountAccess(TargetEntity.APPLICATION.name, application)
        ).isTrue()
      }
    }
  }
}
