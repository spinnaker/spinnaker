package com.netflix.spinnaker.echo.pipelinetriggers.orca

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.util.concurrent.MoreExecutors
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import com.netflix.spinnaker.echo.pipelinetriggers.QuietPeriodIndicator
import com.netflix.spinnaker.echo.pipelinetriggers.runas.RunAsTokenService
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.web.context.AuthenticatedRequestContextProvider
import com.netflix.spinnaker.kork.web.context.RequestContext
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import retrofit2.mock.Calls

class PipelineInitiatorSpec extends Specification {
  def registry = new NoopRegistry()
  def noopDynamicConfigService = new DynamicConfigService.NoopDynamicConfig()
  def orca = Mock(OrcaService)
  def runAsTokenService = Mock(RunAsTokenService)
  def objectMapper = Mock(ObjectMapper)
  def quietPeriodIndicator = Mock(QuietPeriodIndicator)
  def contextProvider = new AuthenticatedRequestContextProvider()
  def activator = Mock(DiscoveryStatusListener)

  Optional<String> capturedSpinnakerUser
  Optional<String> capturedSpinnakerAccounts

  void setup() {
    capturedSpinnakerUser = Optional.empty()
    capturedSpinnakerAccounts = Optional.empty()
  }

  @Unroll
  def "calls orca #expectedTriggerCalls times when enabled=#enabled and suppress=#suppress"() {
    given:
    def dynamicConfigService = Mock(DynamicConfigService)
    def pipelineInitiator = new PipelineInitiator(
      registry, orca, Optional.of(runAsTokenService), MoreExecutors.newDirectExecutorService(), objectMapper, quietPeriodIndicator, dynamicConfigService, activator, 5, 5000
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
    pipelineInitiator.startPipeline(pipeline, PipelineInitiator.TriggerSource.CRON_SCHEDULER)

    then:
    1 * activator.isEnabled() >> upInDiscovery
    _ * dynamicConfigService.isEnabled('scheduler.triggers', true) >> !suppress
    _ * dynamicConfigService.isEnabled("orca", true) >> enabled

    // A fresh short-lived run-as token is minted/propagated from Front50 for each trigger
    // attempt (no remote permission lookup, no account resolution).
    expectedTriggerCalls * runAsTokenService.propagateRunAsToken(expectedSpinnakerUser, _)

    expectedTriggerCalls * orca.trigger(pipeline) >> {
      captureAuthorizationContext()
      Calls.response(new OrcaService.TriggerResponse())
    }

    capturedSpinnakerUser.orElse(null) == (expectedTriggerCalls > 0 ? expectedSpinnakerUser : null)

    where:
    user            | upInDiscovery | enabled | suppress || expectedTriggerCalls || expectedSpinnakerUser
    "anonymous"     | false         | true    | false    || 0                    || "anonymous"      // down in discovery
    "anonymous"     | true          | false   | false    || 0                    || "anonymous"      // orca not enabled
    null            | true          | true    | true     || 0                    || "anonymous"      // cron triggers enabled but suppressed
    "anonymous"     | true          | true    | false    || 1                    || "anonymous"      // triggered as anonymous
    "not-anonymous" | true          | true    | false    || 1                    || "not-anonymous"  // triggered as service account
    null            | true          | true    | false    || 1                    || "anonymous"      // null trigger user defaults to 'anonymous'
  }

  def "propagates auth headers to orca calls without runAs"() {
    given:
    RequestContext context = contextProvider.get()
    def executor = Executors.newFixedThreadPool(2)
    def pipelineInitiator = new PipelineInitiator(
      registry, orca, Optional.of(runAsTokenService), executor, objectMapper, quietPeriodIndicator, noopDynamicConfigService, activator, 5, 5000
    )

    Trigger trigger = (new Trigger.TriggerBuilder().type("cron").build()).atPropagateAuth(true)

    Pipeline pipeline = Pipeline
      .builder()
      .application("application")
      .name("name")
      .id("id")
      .type("pipeline")
      .trigger(trigger)
      .build()

    def user = "super-duper-user"
    def account = "super-duper-account"

    when:
    context.setUser(user)
    context.setAccounts(account)
    pipelineInitiator.startPipeline(pipeline, PipelineInitiator.TriggerSource.CRON_SCHEDULER)
    context.clear()

    // Wait for the trigger to actually be invoked (happens on separate thread)
    executor.shutdown()
    executor.awaitTermination(2, TimeUnit.SECONDS)

    then:
    1 * activator.isEnabled() >> true

    // propagateAuth re-uses the caller's existing identity unchanged - no run-as token is minted.
    0 * runAsTokenService.propagateRunAsToken(_, _)

    1 * orca.trigger(pipeline) >> {
      captureAuthorizationContext()
      Calls.response(new OrcaService.TriggerResponse())
    }

    capturedSpinnakerUser.orElse(null) == user
    capturedSpinnakerAccounts.orElse(null) == account
  }

  @Unroll
  def "calls orca #expectedPlanCalls to plan pipeline if templated"() {
    given:
    def pipelineInitiator = new PipelineInitiator(
      registry, orca, Optional.empty(), MoreExecutors.newDirectExecutorService(), objectMapper, quietPeriodIndicator, noopDynamicConfigService, activator, 5, 5000
    )

    def pipeline = Pipeline.builder()
      .application("application")
      .name("name")
      .id("id")
      .type(type)
      .build()

    def pipelineMap = pipeline as Map

    when:
    pipelineInitiator.startPipeline(pipeline, PipelineInitiator.TriggerSource.CRON_SCHEDULER)

    then:
    1 * activator.isEnabled() >> true
    expectedPlanCalls * orca.plan(_, true) >> Calls.response(pipelineMap)
    objectMapper.convertValue(pipelineMap, Pipeline.class) >> pipeline
    1 * orca.trigger(_) >> {
      captureAuthorizationContext()
      Calls.response( new OrcaService.TriggerResponse())
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
      capturedSpinnakerUser = contextProvider.get().getUser()
      capturedSpinnakerAccounts = contextProvider.get().getAccounts()
  }
}
