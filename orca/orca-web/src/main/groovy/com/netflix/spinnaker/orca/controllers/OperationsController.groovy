/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.kork.exceptions.ConfigurationException
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.clouddriver.service.JobService
import com.netflix.spinnaker.orca.exceptions.OperationFailedException
import com.netflix.spinnaker.orca.exceptions.PipelineTemplateValidationException
import com.netflix.spinnaker.orca.api.pipeline.ExecutionPreprocessor
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.PipelineModelMutator
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateService
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.token.AuthorizationProperties
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier
import com.netflix.spinnaker.security.token.TokenValidationException
import groovy.util.logging.Slf4j
import javassist.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

import jakarta.servlet.http.HttpServletResponse

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import static java.net.HttpURLConnection.HTTP_NOT_FOUND
import static net.logstash.logback.argument.StructuredArguments.value

/**
 * Starts pipeline and ad-hoc task executions (the {@code /orchestrate*} and {@code /ops*}
 * endpoints).
 *
 * <p><b>Trust assumption — this controller enforces no method-level authorization.</b> Unlike
 * {@link TaskController}, whose read/write/execute endpoints are gated by
 * {@code @PreAuthorize}/{@code @PostFilter} SpEL against the {@code spinnakerPermissionEvaluator}
 * (see {@code OrcaSecurityConfig}), none of the execution-launching endpoints here carry any
 * authorization annotation. Access control for these endpoints has always been delegated entirely
 * to Gate (which authorizes the request before proxying it) plus the operational assumption that
 * Orca is not directly reachable by end users. The only per-request authorization decision this
 * controller makes is the role-based filtering of {@code /webhooks/preconfigured}, and that is a
 * read-only visibility filter, not a gate on launching executions.
 *
 * <p>This is a long-standing property, not a regression. It is called out explicitly because, with
 * Fiat no longer in the request path, any operator who mentally treated Orca's own authorization as
 * a second line of defense for execution starts should understand that no such second line exists
 * here — Gate and network isolation remain the sole enforcement points for {@code /orchestrate*}
 * and {@code /ops*}. If direct-to-Orca execution starts must be authorized in the future, these
 * endpoints would need their own {@code @PreAuthorize} checks.
 */
@RestController
@Slf4j
class OperationsController {
  @Autowired
  ExecutionLauncher executionLauncher

  @Autowired(required = false)
  BuildService buildService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ExecutionRepository executionRepository

  @Autowired(required = false)
  PipelineTemplateService pipelineTemplateService

  @Autowired
  ContextParameterProcessor contextParameterProcessor

  @Autowired(required = false)
  List<ExecutionPreprocessor> executionPreprocessors = new ArrayList<>()

  @Autowired(required = false)
  private List<PipelineModelMutator> pipelineModelMutators = new ArrayList<>()

  @Autowired(required = false)
  WebhookService webhookService

  @Autowired(required = false)
  JobService jobService

  @Autowired(required = false)
  ArtifactUtils artifactUtils

  @Autowired(required = false)
  SpinnakerTokenVerifier tokenVerifier

  @Autowired(required = false)
  AuthorizationProperties authorizationProperties

  @Autowired(required = false)
  Front50Service front50Service

  @RequestMapping(value = "/orchestrate", method = RequestMethod.POST)
  Map<String, Object> orchestrate(@RequestBody Map pipeline, HttpServletResponse response) {
    return planOrOrchestratePipeline(pipeline)
  }

  @RequestMapping(value = "/orchestrate/{pipelineConfigId}", method = RequestMethod.POST)
  Map<String, Object> orchestratePipelineConfig(@PathVariable String pipelineConfigId, @RequestBody Map trigger) {
    Map pipelineConfig = buildPipelineConfig(pipelineConfigId, trigger)
    return planOrOrchestratePipeline(pipelineConfig)
  }

  @RequestMapping(value = "/plan", method = RequestMethod.POST)
  Map<String, Object> plan(@RequestBody Map pipeline, @RequestParam("resolveArtifacts") boolean resolveArtifacts, HttpServletResponse response) {
    return planPipeline(pipeline, resolveArtifacts)
  }

  @RequestMapping(value = "/plan/{pipelineConfigId}", method = RequestMethod.POST)
  Map<String, Object> planPipelineConfig(@PathVariable String pipelineConfigId, @RequestParam("resolveArtifacts") boolean resolveArtifacts, @RequestBody Map trigger) {
    Map pipelineConfig = buildPipelineConfig(pipelineConfigId, trigger)
    return planPipeline(pipelineConfig, resolveArtifacts)
  }

  /**
   * Used by echo to mark an execution failure if it fails to materialize the pipeline
   * (e.g. because the artifacts couldn't be resolved)
   *
   * @param pipeline pipeline json
   */
  @RequestMapping(value = '/fail', method = RequestMethod.POST)
  void failPipeline(@RequestBody Map pipeline) {
    String errorMessage = pipeline.remove("errorMessage")

    recordPipelineFailure(pipeline, errorMessage)
  }

  private Map buildPipelineConfig(String pipelineConfigId, Map trigger) {
    if (front50Service == null) {
      throw new UnsupportedOperationException("Front50 is not enabled, no way to retrieve pipeline configs. Fix this by setting front50.enabled: true")
    }

    try {
      Map pipelineConfig = AuthenticatedRequest.allowAnonymous({ Retrofit2SyncCall.execute(front50Service.getPipeline(pipelineConfigId)) })
      pipelineConfig.trigger = trigger
      return pipelineConfig
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == HTTP_NOT_FOUND) {
        throw new NotFoundException("Pipeline config $pipelineConfigId not found")
      }
      throw e
    }
  }

  private Map planOrOrchestratePipeline(Map pipeline) {
    if (pipeline.plan) {
      return planPipeline(pipeline, false)
    } else {
      return orchestratePipeline(pipeline)
    }
  }

  private Map<String, Object> planPipeline(Map pipeline, boolean resolveArtifacts) {
    log.info('Not starting pipeline (plan: true): {}', value("pipelineId", pipeline.id))
    pipelineModelMutators.stream().filter({m -> m.supports(pipeline)}).forEach({m -> m.mutate(pipeline)})
    return parseAndValidatePipeline(pipeline, resolveArtifacts)
  }

  private Map<String, Object> orchestratePipeline(Map pipeline) {
    long startTime = System.currentTimeMillis()

    Exception pipelineError = null
    try {
      pipeline = parseAndValidatePipeline(pipeline)
    } catch (Exception e) {
      pipelineError = e
    }

    def augmentedContext = [
      trigger: pipeline.trigger,
      templateVariables: pipeline.templateVariables ?: [:]
    ]
    def processedPipeline = contextParameterProcessor.processPipeline(pipeline, augmentedContext, false)
    processedPipeline.trigger = objectMapper.convertValue(processedPipeline.trigger, Trigger)

    if (pipelineError == null) {
      def id = startPipeline(processedPipeline)
      log.info(
          "Started pipeline {} based on request body {} (took: {}ms)",
          id,
          renderForLogs(pipeline),
          System.currentTimeMillis() - startTime
      )
      return [ref: "/pipelines/" + id]
    } else {
      def id = markPipelineFailed(processedPipeline, pipelineError)
      log.info("Failed to start pipeline {} based on request body {}", id, renderForLogs(pipeline))
      throw pipelineError
    }
  }

  private void recordPipelineFailure(Map pipeline, String errorMessage) {
    // While we are recording the failure for this execution, we still want to
    // parse/validate/realize the pipeline as best as we can. This way the UI
    // can visualize the pipeline as best as possible.
    // Additionally, if there are any failures we will record all errors for the
    // user to be aware of and address
    Exception pipelineError = null
    try {
      pipeline = parseAndValidatePipeline(pipeline)
    } catch (Exception e) {
      pipelineError = e
    }

    def augmentedContext = [
      trigger: pipeline.trigger,
      templateVariables: pipeline.templateVariables ?: [:]
    ]
    def processedPipeline = contextParameterProcessor.process(pipeline, augmentedContext, false)
    processedPipeline.trigger = objectMapper.convertValue(processedPipeline.trigger, Trigger)

    if (pipelineError != null) {
      pipelineError = new SpinnakerException(errorMessage, pipelineError)
    } else {
      pipelineError = new SpinnakerException(errorMessage)
    }

    markPipelineFailed(processedPipeline, pipelineError)
  }

  Map parseAndValidatePipeline(Map pipeline) {
    return parseAndValidatePipeline(pipeline, true)
  }

  Map parseAndValidatePipeline(Map pipeline, boolean resolveArtifacts) {
    parsePipelineTrigger(pipeline, resolveArtifacts)

    for (ExecutionPreprocessor preprocessor : executionPreprocessors.findAll {
      it.supports(pipeline, ExecutionPreprocessor.Type.PIPELINE)
    }) {
      pipeline = preprocessor.process(pipeline)
    }

    if (pipeline.disabled) {
      throw new ConfigurationException("Pipeline is disabled and cannot be started.")
    }

    def linear = pipeline.stages.every { it.refId == null }
    if (linear) {
      applyStageRefIds(pipeline)
    }

    if (pipeline.errors != null) {
      throw new PipelineTemplateValidationException("Pipeline template is invalid", pipeline.errors as List<Map<String, Object>>)
    }
    return pipeline
  }

  private void parsePipelineTrigger(Map pipeline, boolean resolveArtifacts) {
    if (!(pipeline.trigger instanceof Map)) {
      pipeline.trigger = [:]
      if (pipeline.plan && pipeline.type == "templatedPipeline" && pipelineTemplateService != null) {
        // If possible, initialize the config with a previous execution trigger context, to be able to resolve
        // dynamic parameters in jinja expressions
        try {
          def previousExecution = pipelineTemplateService.retrievePipelineOrNewestExecution(pipeline.executionId, pipeline.id)
          pipeline.trigger = objectMapper.convertValue(previousExecution.trigger, Map)
          pipeline.executionId = previousExecution.id
        } catch (ExecutionNotFoundException | IllegalArgumentException _) {
          log.info("Could not initialize pipeline template config from previous execution context.")
        }
      }
    }

    if (!pipeline.trigger.type) {
      pipeline.trigger.type = "manual"
    }

    if (!pipeline.trigger.user) {
      pipeline.trigger.user = AuthenticatedRequest.getSpinnakerUser().orElse("[anonymous]")
    }

    if (buildService) {
      decorateBuildInfo(pipeline.trigger)
    }

    if (pipeline.trigger.parentPipelineId && !pipeline.trigger.parentExecution) {
      PipelineExecution parentExecution
      try {
        parentExecution = executionRepository.retrieve(PIPELINE, pipeline.trigger.parentPipelineId)
      } catch (ExecutionNotFoundException e) {
        // ignore
      }

      if (parentExecution) {
        pipeline.trigger.isPipeline         = true
        pipeline.trigger.parentStatus       = parentExecution.status
        pipeline.trigger.parentExecution    = parentExecution
        pipeline.trigger.parentPipelineName = parentExecution.name

        pipeline.receivedArtifacts = artifactUtils.getAllArtifacts(parentExecution)
      }
    }

    if (!pipeline.trigger.parameters) {
      pipeline.trigger.parameters = [:]
    }

    if (pipeline.parameterConfig) {
      pipeline.parameterConfig.each {
        pipeline.trigger.parameters[it.name] = pipeline.trigger.parameters.containsKey(it.name) ? pipeline.trigger.parameters[it.name] : it.default
      }
    }

    if (resolveArtifacts) {
      artifactUtils?.resolveArtifacts(pipeline)
    }
  }

  @Deprecated
  private void decorateBuildInfo(Map trigger) {
    // Echo now adds build information to the trigger before sending it to Orca, and manual triggers now default to
    // going through echo (and thus receive build information). We still need this logic to populate build info for
    // manual triggers when the 'triggerViaEcho' deck feature flag is off, or to handle users still hitting the old
    // API endpoint manually, but we should short-circuit if we already have build info.
    if (trigger.master && trigger.job && trigger.buildNumber && !trigger.buildInfo) {
      log.info("Populating build information in Orca for trigger {}.", trigger)
      def buildInfo
      try {
        buildInfo = buildService.getBuild(trigger.buildNumber, trigger.master, trigger.job)
      } catch (SpinnakerHttpException e) {
        if (e.responseCode == 404) {
          throw new ConfigurationException("Build ${trigger.buildNumber} of ${trigger.master}/${trigger.job} not found")
        } else {
          throw new OperationFailedException("Failed to get build ${trigger.buildNumber} of ${trigger.master}/${trigger.job}", e)
        }
      } catch (SpinnakerServerException e){
        throw new OperationFailedException("Failed to get build ${trigger.buildNumber} of ${trigger.master}/${trigger.job}", e)
      }
      if (buildInfo?.artifacts) {
        if (trigger.type == "manual") {
          trigger.artifacts = buildInfo.artifacts
        }
      }
      trigger.buildInfo = buildInfo
      if (trigger.propertyFile) {
        try {
          trigger.properties = buildService.getPropertyFile(
            trigger.buildNumber as Integer,
            trigger.propertyFile as String,
            trigger.master as String,
            trigger.job as String
          )
        } catch (SpinnakerHttpException e) {
          if (e.responseCode == 404) {
            throw new ConfigurationException("Expected properties file " + trigger.propertyFile + " (configured on trigger), but it was missing")
          } else {
            throw new OperationFailedException("Failed to get properties file ${trigger.propertyFile}", e)
          }
        } catch (SpinnakerServerException e){
          throw new OperationFailedException("Failed to get properties file ${trigger.propertyFile}", e)
        }
      }
    }
  }

  @RequestMapping(value = "/ops", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody List<Map> input) {
    def execution = [application: null, name: null, stages: input]
    parsePipelineTrigger(execution, true)
    startTask(execution)
  }

  @RequestMapping(value = "/ops", consumes = "application/context+json", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody Map input) {
    def execution = [application: input.application, name: input.description, stages: input.job, trigger: input.trigger ?: [:]]
    parsePipelineTrigger(execution, true)
    startTask(execution)
  }

  @RequestMapping(value = "/webhooks/preconfigured")
  List<Map<String, Object>> preconfiguredWebhooks() {
    if (!webhookService) {
      return []
    }
    def webhooks = webhookService.preconfiguredWebhooks

    if (webhooks && webhooks.any { it.permissions }) {
      // Role-only decision sourced from the verified identity token (no remote lookup). An admin
      // always sees everything.
      SpinnakerTokenClaims claims = resolveVerifiedClaims()
      if (claims != null) {
        if (!claims.isAdmin()) {
          Set<String> roleNames = claims.getRoles() as Set<String>
          webhooks = webhooks.findAll { it.isAllowed("READ", roleNames) }
        }
      } else if (isStrictAuthorization()) {
        // Fail closed: strict authorization is enabled but no verified token is present, so treat
        // the caller as an anonymous user with no roles (only webhooks readable without any role
        // are shown) rather than showing everything.
        log.warn("Filtering preconfigured webhooks as an anonymous (no-role) user: no verified identity token was present and authz.strict is enabled")
        Set<String> noRoles = Collections.emptySet()
        webhooks = webhooks.findAll { it.isAllowed("READ", noRoles) }
      }
      // else permissive: no verified token during rollout, show all preconfigured webhooks rather
      // than failing closed.
    }

    return webhooks.collect {
      [ label: it.label,
        description: it.description,
        type: it.type,
        waitForCompletion: it.waitForCompletion,
        preconfiguredProperties: it.preconfiguredProperties,
        noUserConfigurableFields: it.noUserConfigurableFields(),
        parameters: it.parameters,
        parameterData: it.parameterData,
      ]
    }
  }

  @RequestMapping(value = "/jobs/preconfigured")
  List<Map<String, Object>> preconfiguredJob() {
    if (!jobService) {
      return []
    }
    // Only allow enabled jobs for configuration in pipelines.
    return jobService.getPreconfiguredStages().findAll { it.enabled } .collect {
        [label                   : it.label,
         description             : it.description,
         type                    : it.type,
         waitForCompletion       : it.waitForCompletion,
         noUserConfigurableFields: true,
         parameters              : it.parameters,
         producesArtifacts       : it.producesArtifacts,
         uiType                  : it.uiType
        ]
    }
  }

  private static void applyStageRefIds(Map<String, Serializable> pipelineConfig) {
    def stages = (List<Map<String, Object>>) pipelineConfig.stages
    stages.eachWithIndex { Map<String, Object> stage, int index ->
      stage.put("refId", String.valueOf(index))
      if (index > 0) {
        stage.put("requisiteStageRefIds", Collections.singletonList(String.valueOf(index - 1)))
      } else {
        stage.put("requisiteStageRefIds", Collections.emptyList())
      }
    }
  }

  private String startPipeline(Map config) {
    injectPipelineOrigin(config)
    def pipeline = executionLauncher.start(PIPELINE, config)
    return pipeline.id
  }

  private String markPipelineFailed(Map config, Exception e) {
    injectPipelineOrigin(config)
    def pipeline = executionLauncher.fail(PIPELINE, config, e)
    return pipeline.id
  }

  private Map<String, String> startTask(Map config) {
    def linear = config.stages.every { it.refId == null }
    if (linear) {
      applyStageRefIds(config)
    }
    injectPipelineOrigin(config)

    for (ExecutionPreprocessor preprocessor : executionPreprocessors.findAll {
      it.supports(config, ExecutionPreprocessor.Type.ORCHESTRATION)
    }) {
      config = preprocessor.process(config)
    }

    def pipeline = null
    try {
      pipeline = executionLauncher.start(ORCHESTRATION, config)
    } finally {
      log.info('started execution {} from requested task: {}', pipeline?.id, renderForLogs(config))
    }
    [ref: "/tasks/${pipeline.id}".toString()]
  }

  private String renderForLogs(Map config) {
    if (!log.isInfoEnabled()) {
      return
    }

    def json = objectMapper.writeValueAsString(config)
    if (json.length() < 20_000) {
      return "(length: ${json.length()}) " + json
    }

    return "(original length: ${json.length()}, truncated to first and last 10k) " +json[0..10_000] + " (...) " + json[-10_000..-1]
  }

  private void injectPipelineOrigin(Map pipeline) {
    if (!pipeline.origin) {
      pipeline.origin = AuthenticatedRequest.spinnakerUserOrigin.orElse('unknown')
    }
  }

  /**
   * Resolve the caller's roles from the verified identity token on the current request, or null when
   * none is available (no verifier wired, no token present, or an invalid token) so the caller can
   * stay permissive during rollout.
   */
  /**
   * Whether fail-closed authorization is in effect: {@code authz.enabled} and {@code authz.strict}
   * are both true. When true, role-only decision points deny (rather than stay permissive) if no
   * verified identity token is available.
   */
  private boolean isStrictAuthorization() {
    return authorizationProperties != null &&
      authorizationProperties.isEnabled() &&
      authorizationProperties.isStrict()
  }

  private SpinnakerTokenClaims resolveVerifiedClaims() {
    if (tokenVerifier == null) {
      return null
    }
    String token = AuthenticatedRequest.get(Header.IDENTITY_TOKEN).orElse(null)
    if (!token) {
      return null
    }
    try {
      return tokenVerifier.verify(token)
    } catch (TokenValidationException e) {
      log.warn("Ignoring invalid identity token while listing preconfigured webhooks", e)
      return null
    }
  }
}
