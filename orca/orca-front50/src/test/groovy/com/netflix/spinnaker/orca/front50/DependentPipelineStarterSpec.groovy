/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.front50

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.ExecutionPreprocessor
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplatePreprocessor
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateErrorHandler
import com.netflix.spinnaker.orca.pipelinetemplate.handler.SchemaVersionHandler
import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.V1SchemaHandlerGroup
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.v2.V2SchemaHandlerGroup
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import org.slf4j.MDC
import org.springframework.context.ApplicationContext
import org.springframework.context.support.StaticApplicationContext
import io.reactivex.rxjava3.core.Observable
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DependentPipelineStarterSpec extends Specification {

  @Subject
  DependentPipelineStarter dependentPipelineStarter

  ObjectMapper mapper = OrcaObjectMapper.newInstance()
  ExecutionRepository executionRepository = Mock(ExecutionRepository)
  ArtifactUtils artifactUtils = Spy(ArtifactUtils, constructorArgs: [mapper, executionRepository, new ContextParameterProcessor()])

  def "should only propagate credentials when explicitly provided"() {
    setup:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered"]
    def parentPipeline = pipeline {
      name = "parent"
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def gotMDC = [:]
    def executionLauncher = Stub(ExecutionLauncher) {
      start(*_) >> { _, p ->
        gotMDC.putAll([
          "X-SPINNAKER-USER": MDC.get("X-SPINNAKER-USER"),
          "X-SPINNAKER-ACCOUNTS": MDC.get("X-SPINNAKER-ACCOUNTS"),
        ])
        return pipeline {
          name = p.name
          id = p.name
          trigger = mapper.convertValue(p.trigger, Trigger)
        }
      }
    }
    ApplicationContext applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      applicationContext,
      mapper,
      new ContextParameterProcessor(),
      Optional.empty(),
      Optional.of(artifactUtils),
      new NoopRegistry()
    )

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null,
      buildAuthenticatedUser("user", ["acct3", "acct4"])
    )
    MDC.clear()

    then:
    result?.name == "triggered"
    gotMDC["X-SPINNAKER-USER"] == "user"
    gotMDC["X-SPINNAKER-ACCOUNTS"] == "acct3,acct4"

    when:
    result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null,
      null
    )
    MDC.clear()

    then:
    result?.name == "triggered"
    gotMDC["X-SPINNAKER-USER"] == null
    gotMDC["X-SPINNAKER-ACCOUNTS"] == null
  }

  def "should propagate dry run flag"() {
    given:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered"]
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("manual", null, "fzlem@netflix.com", [:], [], [], false, true)
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      applicationContext,
      mapper,
      new ContextParameterProcessor(),
      Optional.empty(),
      Optional.of(artifactUtils),
      new NoopRegistry()
    )

    and:
    executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null,
      buildAuthenticatedUser("user", [])
    )

    then:
    result.trigger.dryRun
  }

  def "should find artifacts from triggering pipeline"() {
    given:
    def triggeredPipelineConfig = [
      name             : "triggered",
      id               : "triggered",
      expectedArtifacts: [[
                            id: "id1",
                            matchArtifact: [
                              kind: "gcs",
                              name: "gs://test/file.yaml",
                              type: "gcs/object"
                            ]
                          ]]
    ];
    Artifact testArtifact = Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build()
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test", [:], [testArtifact]);
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      applicationContext,
      mapper,
      new ContextParameterProcessor(),
      Optional.empty(),
      Optional.of(artifactUtils),
      new NoopRegistry()
    )

    and:
    executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }
    artifactUtils.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<Artifact>()
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null,
      parentPipeline,
      [:],
      null,
      buildAuthenticatedUser("user", [])
    )

    then:
    result.trigger.artifacts.size() == 1
    result.trigger.artifacts*.name == ["gs://test/file.yaml"]
  }

  def "should find artifacts from parent pipeline stage"() {
    given:
    def triggeredPipelineConfig = [
      name             : "triggered",
      id               : "triggered",
      expectedArtifacts: [[
                            id: "id1",
                            matchArtifact: [
                              kind: "gcs",
                              name: "gs://test/file.yaml",
                              type: "gcs/object"
                            ]
                          ]]
    ];
    Artifact testArtifact = Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build()
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test")
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
      stage {
        id = "stage1"
        refId = "1"
        outputs.artifacts = [testArtifact]
      }
      stage {
        id = "stage2"
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      applicationContext,
      mapper,
      new ContextParameterProcessor(),
      Optional.empty(),
      Optional.of(artifactUtils),
      new NoopRegistry()
    )

    and:
    executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }
    artifactUtils.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<Artifact>();
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null,
      parentPipeline,
      [:],
      "stage1",
      buildAuthenticatedUser("user", [])
    )

    then:
    result.trigger.artifacts.size() == 1
    result.trigger.artifacts*.name == ["gs://test/file.yaml"]
  }

  def "should find artifacts from triggering pipeline without expected artifacts"() {
    given:
    def triggeredPipelineConfig = [
      name             : "triggered",
      id               : "triggered",
      expectedArtifacts: [[
                            id: "id1",
                            matchArtifact: [
                              kind: "gcs",
                              name: "gs://test/file.yaml",
                              type: "gcs/object"
                            ]
                          ]]
    ]
    Artifact testArtifact1 = Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build()
    Artifact testArtifact2 = Artifact.builder().type("docker/image").name("gcr.io/project/image").build()
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test", [:], [testArtifact1, testArtifact2])
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      applicationContext,
      mapper,
      new ContextParameterProcessor(),
      Optional.empty(),
      Optional.of(artifactUtils),
      new NoopRegistry()
    )

    and:
    executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }
    artifactUtils.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<Artifact>();
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null,
      parentPipeline,
      [:],
      null,
      buildAuthenticatedUser("user", [])
    )

    then:
    result.trigger.artifacts.size() == 2
    result.trigger.artifacts*.name.contains(testArtifact1.name)
    result.trigger.artifacts*.name.contains(testArtifact2.name)
    result.trigger.resolvedExpectedArtifacts.size() == 1
    result.trigger.resolvedExpectedArtifacts.get(0).id.equals("id1")
  }

  def "should find expected artifacts from pipeline trigger"() {
    given:
    def triggeredPipelineConfig = [
        name: "triggered",
        id: "triggered",
        expectedArtifacts: [[
            id: "id1",
            matchArtifact: [
                kind: "gcs",
                name: "gs://test/file.yaml",
                type: "gcs/object"
            ]
        ]],
        triggers: [[
            type: "pipeline",
            pipeline: "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5",
            expectedArtifactIds: ["id1"]
        ], [
            type: "jenkins"
        ]]
    ]
    Artifact testArtifact1 = Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build()
    Artifact testArtifact2 = Artifact.builder().type("docker/image").name("gcr.io/project/image").build()
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test", [:], [testArtifact1, testArtifact2])
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
      pipelineConfigId = "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5"
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
        applicationContext,
        mapper,
        new ContextParameterProcessor(),
        Optional.empty(),
        Optional.of(artifactUtils),
        new NoopRegistry()
    )

    and:
    executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }
    artifactUtils.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<Artifact>();
    }

    when:
    def result = dependentPipelineStarter.trigger(
        triggeredPipelineConfig,
        null,
        parentPipeline,
        [:],
        null,
        buildAuthenticatedUser("user", [])
    )

    then:
    result.trigger.artifacts.size() == 2
    result.trigger.artifacts*.name.contains(testArtifact1.name)
    result.trigger.artifacts*.name.contains(testArtifact2.name)
    result.trigger.resolvedExpectedArtifacts.size() == 1
    result.trigger.resolvedExpectedArtifacts*.boundArtifact.name == [testArtifact1.name]
  }

  def "should ignore expected artifacts from unrelated trigger"() {
    given:
    def triggeredPipelineConfig = [
        name: "triggered",
        id: "triggered",
        expectedArtifacts: [[
            id: "id1",
            matchArtifact: [
                kind: "gcs",
                name: "gs://test/file.yaml",
                type: "gcs/object"
            ]
        ]],
        triggers: [[
            type: "pipeline",
            pipeline: "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5"
        ], [
            type: "jenkins",
            expectedArtifactIds: ["id1"]
        ]]
    ]
    Artifact testArtifact1 = Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build()
    Artifact testArtifact2 = Artifact.builder().type("docker/image").name("gcr.io/project/image").build()
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test", [:], [testArtifact1, testArtifact2])
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
      pipelineConfigId = "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5"
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
        applicationContext,
        mapper,
        new ContextParameterProcessor(),
        Optional.empty(),
        Optional.of(artifactUtils),
        new NoopRegistry()
    )

    and:
    executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }
    artifactUtils.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<Artifact>();
    }

    when:
    def result = dependentPipelineStarter.trigger(
        triggeredPipelineConfig,
        null,
        parentPipeline,
        [:],
        null,
        buildAuthenticatedUser("user", [])
    )

    then:
    result.trigger.artifacts.size() == 2
    result.trigger.artifacts*.name.contains(testArtifact1.name)
    result.trigger.artifacts*.name.contains(testArtifact2.name)
    result.trigger.resolvedExpectedArtifacts.size() == 1
    result.trigger.resolvedExpectedArtifacts.get(0).id.equals("id1")
  }

  def "should find expected artifacts from parent pipeline trigger if triggered by pipeline stage"() {
    given:
    def triggeredPipelineConfig = [
        name: "triggered",
        id: "triggered",
        expectedArtifacts: [],
        triggers: [],
    ]
    Artifact testArtifact1 = Artifact.builder().type("gcs/object").name("gs://test/file.yaml").build()
    Artifact testArtifact2 = Artifact.builder().type("docker/image").name("gcr.io/project/image").version("42").build()
    Artifact testArtifact3 = Artifact.builder().type("docker/image").name("gcr.io/project/image").version("1337").build()
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test", [:], [testArtifact1, testArtifact2])
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
      pipelineConfigId = "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5"
      stage {
        id = "stage1"
        refId = "1"
      }
      stage {
        id = "stage2"
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
    }
    parentPipeline.stageByRef("1").setOutputs([artifacts: [testArtifact3]])

    def uuid = "8f241d2a-7fee-4a95-8d84-0a508222032c"
    def expectedImage = Artifact.builder().type("docker/image").name("gcr.io/project/image").build()
    ArrayList<ExpectedArtifact> expectedArtifacts = [
        ExpectedArtifact.builder().id(uuid).matchArtifact(expectedImage).build()
    ]
    parentPipeline.trigger.setOther("expectedArtifacts", expectedArtifacts)
    parentPipeline.trigger.resolvedExpectedArtifacts = expectedArtifacts
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
        applicationContext,
        mapper,
        new ContextParameterProcessor(),
        Optional.empty(),
        Optional.of(artifactUtils),
        new NoopRegistry()
    )

    and:
    executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        name = p.name
        id = p.name
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }
    artifactUtils.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<Artifact>();
    }

    when:
    def result = dependentPipelineStarter.trigger(
        triggeredPipelineConfig,
        null,
        parentPipeline,
        [:],
        "stage2",
        buildAuthenticatedUser("user", [])
    )

    then:
    result.trigger.artifacts.size() == 3
    result.trigger.artifacts*.name.contains(testArtifact1.name)
    result.trigger.artifacts*.name.contains(testArtifact2.name)
    result.trigger.artifacts*.name.contains(testArtifact3.name)
    result.trigger.artifacts.findAll { it.name == "gcr.io/project/image" }.version.containsAll(["42", "1337"])
  }

  def "should fail pipeline when parent pipeline does not provide expected artifacts"() {
    given:
    def artifact = Artifact.builder().type("embedded/base64").name("baked-manifest").build()
    def expectedArtifactId = "826018cd-e278-4493-a6a5-4b0a0166a843"
    def expectedArtifact = ExpectedArtifact
        .builder()
        .id(expectedArtifactId)
        .matchArtifact(artifact)
        .build()

    def parentPipeline = pipeline {
      name = "my-parent-pipeline"
      authentication = new PipelineExecution.AuthenticationDetails("username", "account1")
      pipelineConfigId = "fe0b3537-3101-46a1-8e08-ab57cf65a207"
      stage {
        id = "my-stage-1"
        refId = "1"
        // not passing artifacts
      }
    }

    def triggeredPipelineConfig = [
        name             : "triggered-by-stage",
        id               : "triggered-id",
        stages           : [
            [
                name: "My Stage",
                type: "bakeManifest",
            ]
        ],
        expectedArtifacts: [
            expectedArtifact
        ],
        triggers         : [
            [
                type               : "pipeline",
                pipeline           : parentPipeline.pipelineConfigId,
                expectedArtifactIds: [expectedArtifactId]
            ]
        ],
    ]

    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
        applicationContext,
        mapper,
        new ContextParameterProcessor(),
        Optional.empty(),
        Optional.of(artifactUtils),
        new NoopRegistry()
    )

    and:
    def error
    executionLauncher.fail(_, _, _) >> { PIPELINE, processedPipeline, artifactError ->
      error = artifactError
      return pipeline {
        name = processedPipeline.name
        id = processedPipeline.name
        trigger = mapper.convertValue(processedPipeline.trigger, Trigger)
      }
    }

    when:
    def result = dependentPipelineStarter.trigger(
        triggeredPipelineConfig,
        null,
        parentPipeline,
        [:],
        "my-stage-1",
        buildAuthenticatedUser("username", [])
    )

    then:
    1 * artifactUtils.resolveArtifacts(_)
    error != null
    error instanceof InvalidRequestException
    error.message == "Unmatched expected artifact " + expectedArtifact + " could not be resolved."
    result.trigger.artifacts.size() == 0
  }

  def "should resolve expressions in trigger"() {
    given:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered", parameterConfig: [[name: 'a', default: '${2 == 2}']]]
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("manual", null, "fzlem@netflix.com", [:], [], [], false, true)
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      applicationContext,
      mapper,
      new ContextParameterProcessor(),
      Optional.empty(),
      Optional.of(artifactUtils),
      new NoopRegistry()
    )

    and:
    executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null,
      buildAuthenticatedUser("user", [])
    )

    then:
    result.trigger.parameters.a == true
  }

  private static PipelineExecution.AuthenticationDetails buildAuthenticatedUser(String email, List<String> allowedAccounts) {
    def authenticatedUser = new PipelineExecution.AuthenticationDetails(email, allowedAccounts)

    return authenticatedUser
  }

  def "should resolve expressions in trigger using the context of the trigger"() {
    given:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered", parameterConfig: [[name: 'a', default: '${trigger.type}']]]
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("manual", null, "fzlem@netflix.com", [:], [], [], false, true)
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      applicationContext,
      mapper,
      new ContextParameterProcessor(),
      Optional.empty(),
      Optional.of(artifactUtils),
      new NoopRegistry()
    )

    and:
    executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        trigger = mapper.convertValue(p.trigger, Trigger)
      }
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null,
      buildAuthenticatedUser("user", [])
    )

    then:
    result.trigger.parameters.a == "pipeline"
  }

  def "should trigger v1 templated pipelines with dynamic source using prior artifact"() {
    given:
    def triggeredPipelineConfig = [
      id: "triggered",
      type: "templatedPipeline",
      config: [
        pipeline: [
          application: "covfefe",
          name: "Templated pipeline",
          template: [
            source: "{% for artifact in trigger.artifacts %}{% if artifact.type == 'spinnaker-pac' && artifact.name == 'wait' %}{{ artifact.reference }}{% endif %}{% endfor %}"
          ]
        ],
        schema: "1"
      ],
      expectedArtifacts: [[
        defaultArtifact: [
          customKind: true,
          id: "091b682c-10ac-441a-97f2-659113128960",
        ],
        displayName: "friendly-gecko-6",
        id: "28907e3a-e529-473d-bf2d-b3737c9d6dc6",
        matchArtifact: [
          customKind: true,
          id: "daef2911-ea5c-4098-aa07-ee2535b2788d",
          name: "wait",
          type: "spinnaker-pac"
        ],
        useDefaultArtifact: false,
        usePriorArtifact: true
      ]],
      triggers: [[
           type                : "pipeline",
           pipeline            : "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5",
           expectedArtifactIds : ["28907e3a-e529-473d-bf2d-b3737c9d6dc6"]
       ], [
           type                : "jenkins",
           expectedArtifactIds : ["unrelated_id"]
       ]]
    ]
    def triggeredPipelineTemplate = mapper.convertValue([
      schema: "1",
      id: "barebones",
      stages: [[
        id: "wait1",
        type: "wait",
        name: "Wait for 5 seconds",
        config: [
          waitTime: 5
        ]
      ]]
    ], PipelineTemplate)
    def priorExecution = pipeline {
      id = "01DCKTEZPRCMFV1H35EDFC62RG"
      trigger = new DefaultTrigger("manual", null, "user@acme.com", [:], [
        Artifact.builder()
        .customKind(false)
        .metadata([fileName: "wait.0.1.yml"])
        .name("wait")
        .reference("https://artifactory.acme.com/spinnaker-pac/wait.0.1.yml")
        .type("spinnaker-pac")
        .version("spinnaker-pac")
        .build()
      ])
    }
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("manual", null, "user@schibsted.com", [:], [], [], false, true)
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
      pipelineConfigId = "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5"
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def templateLoader = Mock(TemplateLoader)
    def applicationContext = new StaticApplicationContext()
    def renderer = new JinjaRenderer(mapper, Mock(Front50Service), [])
    def registry = new NoopRegistry()
    def parameterProcessor = new ContextParameterProcessor()
    def pipelineTemplatePreprocessor = new PipelineTemplatePreprocessor(
      mapper,
      new SchemaVersionHandler(
        new V1SchemaHandlerGroup(
          templateLoader,
          renderer,
          mapper,
          registry),
        Mock(V2SchemaHandlerGroup)),
      new PipelineTemplateErrorHandler(),
      registry)
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      applicationContext,
      mapper,
      parameterProcessor,
      Optional.of([pipelineTemplatePreprocessor] as List<ExecutionPreprocessor>),
      Optional.of(artifactUtils),
      registry
    )

    and:
    1 * executionLauncher.start(*_) >> { _, p ->
      return pipeline {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List, StageExecution)
        trigger = mapper.convertValue(p.trigger, Trigger)
        stages.addAll(mapper.convertValue(p.stages, type))
      }
    }
    1 * templateLoader.load(_ as TemplateConfiguration.TemplateSource, _, _) >> [triggeredPipelineTemplate]
    1 * executionRepository.retrievePipelinesForPipelineConfigId("triggered", _ as ExecutionRepository.ExecutionCriteria) >>
      Observable.just(priorExecution)

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null,
      buildAuthenticatedUser("user", [])
    )

    then:
    result.stages.size() == 1
    result.stages[0].type == "wait"
    result.stages[0].refId == "wait1"
    result.stages[0].name == "Wait for 5 seconds"
  }

  def "should trigger v1 templated pipelines with dynamic source using inherited expectedArtifacts"() {
    given:
    def triggeredPipelineConfig = [
      id: "triggered",
      type: "templatedPipeline",
      config: [
        configuration: [
          inherit: ["expectedArtifacts", "triggers"]
        ],
        pipeline: [
          application: "covfefe",
          name: "Templated pipeline",
          template: [
            source: "{% for artifact in trigger.artifacts %}{% if artifact.type == 'spinnaker-pac' && artifact.name == 'wait' %}{{ artifact.reference }}{% endif %}{% endfor %}"
          ]
        ],
        schema: "1"
      ],
      triggers: [[
          type                : "pipeline",
          pipeline            : "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5",
          expectedArtifactIds : ["helm-chart"]
      ]]
    ]
    def triggeredPipelineTemplate = mapper.convertValue([
      schema: "1",
      id: "barebones",
      configuration: [
        expectedArtifacts: [[
                              defaultArtifact: [
                                customKind: true
                              ],
                              id: "helm-chart",
                              displayName: "helm-chart",
                              matchArtifact: [
                                customKind: true,
                                type: "http/file",
                                name: "artifact-name"
                              ],
                              useDefaultArtifact: false,
                              usePriorArtifact: false
        ]]
      ],
      stages: [[
                 id: "bake-manifest",
                 type: "bakeManifest",
                 name: "Bake manifest",
                 config: [
                   templateRenderer: "HELM2",
                   inputArtifacts: [[
                                      account: "my-account",
                                      id: "helm-chart"
                                    ]],
                   expectedArtifacts: [[
                                         id: "baked-manifest",
                                         matchArtifact: [
                                           kind: "base64",
                                           name: "baked-manifest",
                                           type: ArtifactTypes.EMBEDDED_BASE64.getMimeType()
                                         ],
                                         useDefaultArtifact: false
                                       ]],
                   namespace: "a-namespace",
                   outputName: "baked-manifest"
                 ]
               ]]

    ], PipelineTemplate)

    Artifact testArtifact = Artifact.builder()
      .type("http/file")
      .name("artifact-name")
      .customKind(true)
      .reference("a-reference")
      .build()
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new DefaultTrigger("webhook", null, "test", [:], [testArtifact])
      authentication = new PipelineExecution.AuthenticationDetails("parentUser", "acct1", "acct2")
      pipelineConfigId = "5e96d1e8-a3c0-4458-b3a4-fda17e0d5ab5"
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def templateLoader = Mock(TemplateLoader)
    def applicationContext = new StaticApplicationContext()
    def renderer = new JinjaRenderer(mapper, Mock(Front50Service), [])
    def registry = new NoopRegistry()
    def parameterProcessor = new ContextParameterProcessor()
    def pipelineTemplatePreprocessor = new PipelineTemplatePreprocessor(
      mapper,
      new SchemaVersionHandler(
        new V1SchemaHandlerGroup(
          templateLoader,
          renderer,
          mapper,
          registry),
        Mock(V2SchemaHandlerGroup)),
      new PipelineTemplateErrorHandler(),
      registry)
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      applicationContext,
      mapper,
      parameterProcessor,
      Optional.of([pipelineTemplatePreprocessor] as List<ExecutionPreprocessor>),
      Optional.of(artifactUtils),
      registry
    )

    and:
    def execution
    1 * executionLauncher.start(*_) >> { _, config ->
      execution = config
      return pipeline {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List, StageExecution)
        trigger = mapper.convertValue(execution.trigger, Trigger)
        stages.addAll(mapper.convertValue(execution.stages, type))
      }
    }
    1 * templateLoader.load(_ as TemplateConfiguration.TemplateSource, _, _) >> [triggeredPipelineTemplate]

    artifactUtils.getArtifactsForPipelineId(*_) >> {
      return new ArrayList<>()
    }

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      null,
      buildAuthenticatedUser("user", [])
    )

    then:
    execution.expectedArtifacts.size() == 1
    execution.expectedArtifacts[0].id == "helm-chart"
    result.trigger.resolvedExpectedArtifacts.size() == 1
    result.trigger.resolvedExpectedArtifacts[0].id == "helm-chart"
  }
}
