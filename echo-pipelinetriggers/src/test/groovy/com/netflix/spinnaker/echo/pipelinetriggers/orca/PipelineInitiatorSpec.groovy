package com.netflix.spinnaker.echo.pipelinetriggers.orca

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.util.concurrent.MoreExecutors
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.QuietPeriodIndicator
import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Account
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.security.AuthenticatedRequest
import spock.lang.Specification
import spock.lang.Unroll

class PipelineInitiatorSpec extends Specification {
  def registry = new NoopRegistry()
  def orca = Mock(OrcaService)
  def fiatPermissionEvaluator = Mock(FiatPermissionEvaluator)
  def fiatStatus = Mock(FiatStatus)
  def objectMapper = Mock(ObjectMapper)
  def quietPeriodIndicator = Mock(QuietPeriodIndicator)

  Optional<String> capturedSpinnakerUser
  Optional<String> capturedSpinnakerAccounts

  def userPermissions = [
      "anonymous": new UserPermission.View(
          accounts: [
              account("account1", ["READ"]),
              account("account2", ["READ", "WRITE"]),
              account("account3", ["READ", "WRITE"])
          ] as Set<Account.View>
      ),
      "not-anonymous": new UserPermission.View(
          accounts: [
              account("account1", ["READ", "WRITE"]),
              account("account2", ["READ", "WRITE"]),
              account("account3", ["READ", "WRITE"])
          ] as Set<Account.View>
      )
  ]

  void setup() {
    capturedSpinnakerUser = Optional.empty()
    capturedSpinnakerAccounts = Optional.empty()
  }

  @Unroll
  def "calls orca #expectedTriggerCalls times when enabled=#enabled flag"() {
    given:
    def pipelineInitiator = new PipelineInitiator(
      registry, orca, Optional.of(fiatPermissionEvaluator), fiatStatus, MoreExecutors.newDirectExecutorService(), objectMapper, quietPeriodIndicator, enabled, 5, 5000
    )

    def pipeline = Pipeline
        .builder()
        .application("application")
        .name("name")
        .id("id")
        .type("pipeline")
        .trigger(
            new Trigger.TriggerBuilder().type("cron").runAsUser(user).build()
        )
        .build()

    when:
    pipelineInitiator.startPipeline(pipeline, PipelineInitiator.TriggerSource.SCHEDULER)

    then:
    _ * fiatStatus.isEnabled() >> { return enabled }
    _ * fiatStatus.isLegacyFallbackEnabled() >> { return legacyFallbackEnabled }

    (legacyFallbackEnabled ? 1 : 0) * fiatPermissionEvaluator.getPermission(user ?: "anonymous") >> {
      return userPermissions.get(user ?: "anonymous")
    }

    expectedTriggerCalls * orca.trigger(pipeline) >> {
      captureAuthorizationContext()
      return new OrcaService.TriggerResponse()
    }

    capturedSpinnakerUser.orElse(null) == expectedSpinnakerUser
    capturedSpinnakerAccounts.orElse(null)?.split(",") as Set<String> == expectedSpinnakerAccounts?.split(",") as Set<String>

    where:
    user            | enabled | legacyFallbackEnabled || expectedTriggerCalls || expectedSpinnakerUser || expectedSpinnakerAccounts
    "anonymous"     | false   | false                 || 0                    || null                  || null                          // fiat is not enabled
    "anonymous"     | true    | false                 || 1                    || "anonymous"           || null                          // fallback disabled (no accounts)
    "anonymous"     | true    | true                  || 1                    || "anonymous"           || "account2,account3"           // fallback enabled (all WRITE accounts)
    "not-anonymous" | true    | true                  || 1                    || "not-anonymous"       || "account1,account2,account3"  // fallback enabled (all WRITE accounts)
    null            | true    | true                  || 1                    || "anonymous"           || "account2,account3"           // null trigger user should default to 'anonymous'
  }

  @Unroll
  def "calls orca #expectedPlanCalls to plan pipeline if templated"() {
    given:
    def pipelineInitiator = new PipelineInitiator(
      registry, orca, Optional.empty(), fiatStatus, MoreExecutors.newDirectExecutorService(), objectMapper, quietPeriodIndicator, true, 5, 5000
    )

    def pipeline = Pipeline.builder()
      .application("application")
      .name("name")
      .id("id")
      .type(type)
      .build()

    def pipelineMap = pipeline as Map

    when:
    pipelineInitiator.startPipeline(pipeline, PipelineInitiator.TriggerSource.SCHEDULER)

    then:
    1 * fiatStatus.isEnabled() >> { return true }
    expectedPlanCalls * orca.plan(_, true) >> pipelineMap
    objectMapper.convertValue(pipelineMap, Pipeline.class) >> pipeline
    1 * orca.trigger(_) >> {
      captureAuthorizationContext()
      return new OrcaService.TriggerResponse()
    }

    capturedSpinnakerUser.orElse(null) == expectedSpinnakerUser
    capturedSpinnakerAccounts.orElse(null) == expectedSpinnakerAccounts

    where:
    type                || expectedPlanCalls || expectedSpinnakerUser || expectedSpinnakerAccounts
    "pipeline"          || 0                 || "anonymous"           || null
    "templatedPipeline" || 1                 || "anonymous"           || null
    null                || 0                 || "anonymous"           || null
  }

  private captureAuthorizationContext() {
      capturedSpinnakerUser = AuthenticatedRequest.getSpinnakerUser()
      capturedSpinnakerAccounts = AuthenticatedRequest.getSpinnakerAccounts()
  }

  private static Account.View account(String name, Collection<String> authorizations) {
    def accountView = new Account.View()

    accountView.name = name
    accountView.authorizations = authorizations.collect { Authorization.valueOf(it) }

    return accountView
  }
}
