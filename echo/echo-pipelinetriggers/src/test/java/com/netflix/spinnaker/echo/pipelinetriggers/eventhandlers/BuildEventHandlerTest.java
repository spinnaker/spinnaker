/*
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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.build.BuildInfoService;
import com.netflix.spinnaker.echo.config.IgorConfigurationProperties;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.model.trigger.BuildEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.services.IgorService;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import retrofit2.mock.Calls;

class BuildEventHandlerTest {

  private static final String MASTER_NAME = "jenkins-server";
  private static final String JOB_NAME = "my-job";
  private static final int BUILD_NUMBER = 7;
  private static final String PROPERTY_FILE = "property-file";
  private static final Map<String, Object> BUILD_INFO = Map.of("abc", 123);
  private static final Map<String, Object> PROPERTIES =
      Map.of("def", 456, "branch", "feature/my-thing");
  private static final Map<String, Object> CONSTRAINTS =
      Map.of(
          "def", "^[0-9]*$", // def must be a positive number
          "branch", "^(feature)/.*$" // only trigger on branch name like "feature/***"
          );

  private final NoopRegistry registry = new NoopRegistry();
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
      EchoObjectMapper.getInstance();
  private final TestEventHandlerSupport handlerSupport = new TestEventHandlerSupport();
  private final AtomicInteger nextId = new AtomicInteger(1);

  private IgorService igorService;
  private BuildInfoService buildInformation;
  private FiatPermissionEvaluator fiatPermissionEvaluator;
  private BuildEventHandler eventHandler;

  @BeforeEach
  void setUp() {
    igorService = mock(IgorService.class);
    IgorConfigurationProperties props = new IgorConfigurationProperties();
    props.setJobNameAsQueryParameter(false);
    buildInformation = new BuildInfoService(igorService, new RetrySupport(), props);
    fiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(fiatPermissionEvaluator.hasPermission(
            any(String.class), any(String.class), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(true);
    eventHandler =
        new BuildEventHandler(
            registry, objectMapper, Optional.of(buildInformation), fiatPermissionEvaluator);
  }

  private Pipeline createPipelineWith(Trigger... triggers) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .id(String.valueOf(nextId.getAndIncrement()))
        .triggers(List.of(triggers))
        .build();
  }

  private static BuildEvent createBuildEventWith(BuildEvent.Result result) {
    BuildEvent.Build build =
        result != null
            ? new BuildEvent.Build(result == BuildEvent.Result.BUILDING, 1, result, null, List.of())
            : null;
    BuildEvent res = new BuildEvent();
    res.setContent(new BuildEvent.Content(new BuildEvent.Project("job", build), "master"));
    Metadata details = new Metadata();
    details.setType(BuildEvent.TYPE);
    res.setDetails(details);
    return res;
  }

  private static BuildEvent getBuildEvent() {
    BuildEvent.Build build =
        new BuildEvent.Build(false, BUILD_NUMBER, BuildEvent.Result.SUCCESS, null, null);
    BuildEvent.Project project = new BuildEvent.Project(JOB_NAME, build);
    BuildEvent event = new BuildEvent();
    event.setContent(new BuildEvent.Content(project, MASTER_NAME));
    return event;
  }

  private static Trigger enabledJenkinsTrigger() {
    return Trigger.builder().enabled(true).type("jenkins").master("master").job("job").build();
  }

  private static Trigger disabledJenkinsTrigger() {
    return Trigger.builder().enabled(false).type("jenkins").master("master").job("job").build();
  }

  private static Trigger enabledJenkinsTriggerWithRunAsUser() {
    return enabledJenkinsTrigger().withRunAsUser("user@managed-service-account");
  }

  private static Trigger enabledTravisTrigger() {
    return Trigger.builder().enabled(true).type("travis").master("master").job("job").build();
  }

  private static Trigger disabledTravisTrigger() {
    return Trigger.builder().enabled(false).type("travis").master("master").job("job").build();
  }

  private static Trigger enabledConcourseTrigger() {
    return Trigger.builder().enabled(true).type("concourse").master("master").job("job").build();
  }

  private static Trigger disabledConcourseTrigger() {
    return Trigger.builder().enabled(false).type("concourse").master("master").job("job").build();
  }

  private static Trigger nonJenkinsTrigger() {
    return Trigger.builder().enabled(true).type("not jenkins").master("master").job("job").build();
  }

  private static Trigger enabledStashTrigger() {
    return Trigger.builder()
        .enabled(true)
        .type("git")
        .source("stash")
        .project("project")
        .slug("slug")
        .build();
  }

  private static Trigger enabledBitBucketTrigger() {
    return Trigger.builder()
        .enabled(true)
        .type("git")
        .source("bitbucket")
        .project("project")
        .slug("slug")
        .build();
  }

  private static Stream<Arguments> successfulBuildTriggerParams() {
    return Stream.of(
        Arguments.of(
            createBuildEventWith(BuildEvent.Result.SUCCESS), enabledJenkinsTrigger(), "jenkins"),
        Arguments.of(
            createBuildEventWith(BuildEvent.Result.SUCCESS), enabledTravisTrigger(), "travis"));
  }

  @ParameterizedTest(name = "triggers pipelines for successful builds for {2}")
  @MethodSource("successfulBuildTriggerParams")
  void triggersPipelinesForSuccessfulBuilds(BuildEvent event, Trigger trigger, String triggerType)
      throws TimeoutException {
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getApplication()).isEqualTo(pipeline.getApplication());
    assertThat(matchingPipelines.get(0).getName()).isEqualTo(pipeline.getName());
  }

  private static Stream<Arguments> attachesTriggerParams() {
    return Stream.of(
        Arguments.of("jenkins", enabledJenkinsTrigger(), nonJenkinsTrigger()),
        Arguments.of("travis", enabledTravisTrigger(), nonJenkinsTrigger()),
        Arguments.of("concourse", enabledConcourseTrigger(), nonJenkinsTrigger()));
  }

  @ParameterizedTest(name = "attaches {0} trigger to the pipeline")
  @MethodSource("attachesTriggerParams")
  void attachesTriggerToThePipeline(String triggerType, Trigger expectedTrigger, Trigger other)
      throws TimeoutException {
    BuildEvent event = createBuildEventWith(BuildEvent.Result.SUCCESS);
    Pipeline pipeline = createPipelineWith(expectedTrigger, other);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);

    List<Pipeline> result = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getTrigger().getType()).isEqualTo(expectedTrigger.getType());
    assertThat(result.get(0).getTrigger().getMaster()).isEqualTo(expectedTrigger.getMaster());
    assertThat(result.get(0).getTrigger().getJob()).isEqualTo(expectedTrigger.getJob());
    assertThat(result.get(0).getTrigger().getBuildNumber())
        .isEqualTo(event.getContent().getProject().getLastBuild().getNumber());
  }

  @Test
  void eventCanTriggerMultiplePipelines() throws TimeoutException {
    BuildEvent event = createBuildEventWith(BuildEvent.Result.SUCCESS);
    List<Pipeline> pipelines =
        List.of(
            Pipeline.builder()
                .application("application")
                .name("pipeline1")
                .id("id")
                .triggers(List.of(enabledJenkinsTrigger()))
                .build(),
            Pipeline.builder()
                .application("application")
                .name("pipeline2")
                .id("id")
                .triggers(List.of(enabledJenkinsTrigger()))
                .build());
    PipelineCache cache = handlerSupport.pipelineCache(pipelines);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, cache);

    assertThat(matchingPipelines).hasSize(pipelines.size());
  }

  @Test
  void eventTriggersPipelineWith2TriggersOnlyOnce() throws TimeoutException {
    Pipeline pipeline =
        Pipeline.builder()
            .application("application")
            .name("pipeline")
            .id("id")
            .triggers(
                List.of(enabledJenkinsTrigger(), enabledJenkinsTrigger().withJob("someOtherJob")))
            .build();
    PipelineCache cache = handlerSupport.pipelineCache(pipeline);
    BuildEvent event = createBuildEventWith(BuildEvent.Result.SUCCESS);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, cache);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getTrigger().getJob()).isEqualTo("job");
  }

  @Test
  void eventTriggersPipelineWith2MatchingTriggersOnlyOnce() throws TimeoutException {
    Pipeline pipeline =
        Pipeline.builder()
            .application("application")
            .name("pipeline")
            .id("id")
            .triggers(List.of(enabledJenkinsTrigger(), enabledJenkinsTrigger()))
            .build();
    PipelineCache cache = handlerSupport.pipelineCache(pipeline);
    BuildEvent event = createBuildEventWith(BuildEvent.Result.SUCCESS);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, cache);

    assertThat(matchingPipelines).hasSize(1);
  }

  private static Stream<Arguments> doesNotTriggerForBuildResultParams() {
    return Stream.of(
        Arguments.of(BuildEvent.Result.BUILDING),
        Arguments.of(BuildEvent.Result.FAILURE),
        Arguments.of(BuildEvent.Result.ABORTED),
        Arguments.of((Object) null));
  }

  @ParameterizedTest(name = "does not trigger pipelines for {0} builds")
  @MethodSource("doesNotTriggerForBuildResultParams")
  void doesNotTriggerPipelinesForBuilds(BuildEvent.Result result) throws TimeoutException {
    Pipeline pipeline = createPipelineWith(enabledJenkinsTrigger());
    BuildEvent event = createBuildEventWith(result);

    List<Pipeline> matchingPipelines =
        eventHandler.getMatchingPipelines(event, handlerSupport.pipelineCache(pipeline));

    assertThat(matchingPipelines).isEmpty();
  }

  private static Stream<Arguments> doesNotTriggerParams() {
    return Stream.of(
        Arguments.of(disabledJenkinsTrigger(), "disabled jenkins"),
        Arguments.of(disabledTravisTrigger(), "disabled travis"),
        Arguments.of(disabledConcourseTrigger(), "disabled concourse"),
        Arguments.of(nonJenkinsTrigger(), "non-Jenkins"),
        Arguments.of(enabledStashTrigger(), "stash"),
        Arguments.of(enabledBitBucketTrigger(), "bitbucket"),
        Arguments.of(enabledJenkinsTrigger().withMaster("FOO"), "different master"),
        Arguments.of(enabledJenkinsTrigger().withJob("FOO"), "different job"));
  }

  @ParameterizedTest(name = "does not trigger {1} pipelines")
  @MethodSource("doesNotTriggerParams")
  void doesNotTriggerPipelines(Trigger trigger, String description) throws TimeoutException {
    Pipeline pipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(pipeline);
    BuildEvent event = createBuildEventWith(BuildEvent.Result.SUCCESS);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).isEmpty();
  }

  private static Stream<Arguments> missingFieldParams() {
    return Stream.of(
        Arguments.of(enabledJenkinsTrigger().withMaster(null), "master", "jenkins"),
        Arguments.of(enabledJenkinsTrigger().withJob(null), "job", "jenkins"),
        Arguments.of(enabledTravisTrigger().withMaster(null), "master", "travis"),
        Arguments.of(enabledTravisTrigger().withJob(null), "job", "travis"));
  }

  @ParameterizedTest(
      name = "does not trigger a pipeline that has an enabled {2} trigger with missing {1}")
  @MethodSource("missingFieldParams")
  void doesNotTriggerPipelineWithMissingField(Trigger trigger, String field, String triggerType)
      throws TimeoutException {
    BuildEvent event = createBuildEventWith(BuildEvent.Result.SUCCESS);
    Pipeline goodPipeline = createPipelineWith(enabledJenkinsTrigger());
    Pipeline badPipeline = createPipelineWith(trigger);
    PipelineCache pipelines = handlerSupport.pipelineCache(badPipeline, goodPipeline);

    List<Pipeline> matchingPipelines = eventHandler.getMatchingPipelines(event, pipelines);

    assertThat(matchingPipelines).hasSize(1);
    assertThat(matchingPipelines.get(0).getId()).isEqualTo(goodPipeline.getId());
  }

  @Test
  void fetchesBuildInfoIfDefined() {
    Trigger trigger =
        enabledJenkinsTrigger()
            .withMaster(MASTER_NAME)
            .withJob(JOB_NAME)
            .withBuildNumber(BUILD_NUMBER);
    BuildEvent event = getBuildEvent();

    when(igorService.getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME))
        .thenReturn(Calls.response(BUILD_INFO));

    Function<Trigger, Trigger> triggerBuilder = eventHandler.buildTrigger(event);
    Trigger outputTrigger = triggerBuilder.apply(trigger);

    verify(igorService, times(1)).getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME);
    assertThat(outputTrigger.getBuildInfo()).isEqualTo(BUILD_INFO);
  }

  @Test
  void getBuildInfoMethodGetsJobNameFromQueryWhenFlagIsTrue() {
    Trigger trigger = enabledJenkinsTrigger().withMaster(MASTER_NAME).withBuildNumber(BUILD_NUMBER);
    BuildEvent event = getBuildEvent();

    RetrySupport retrySupport = new RetrySupport();
    IgorConfigurationProperties configProperties = new IgorConfigurationProperties();
    configProperties.setJobNameAsQueryParameter(true);
    BuildInfoService buildInfoService =
        new BuildInfoService(igorService, retrySupport, configProperties);
    BuildEventHandler buildEventHandler =
        new BuildEventHandler(
            registry, objectMapper, Optional.of(buildInfoService), fiatPermissionEvaluator);

    when(igorService.getBuildStatusWithJobQueryParameter(BUILD_NUMBER, MASTER_NAME, JOB_NAME))
        .thenReturn(Calls.response(BUILD_INFO));

    Function<Trigger, Trigger> triggerBuilder = buildEventHandler.buildTrigger(event);
    Trigger outputTrigger = triggerBuilder.apply(trigger);

    verify(igorService, times(1))
        .getBuildStatusWithJobQueryParameter(BUILD_NUMBER, MASTER_NAME, JOB_NAME);
    assertThat(outputTrigger.getBuildInfo()).isEqualTo(BUILD_INFO);
  }

  @Test
  void fetchesPropertyFileIfDefined() {
    Trigger trigger =
        enabledJenkinsTrigger()
            .withMaster(MASTER_NAME)
            .withJob(JOB_NAME)
            .withBuildNumber(BUILD_NUMBER)
            .withPropertyFile(PROPERTY_FILE);
    BuildEvent event = getBuildEvent();

    when(igorService.getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME))
        .thenReturn(Calls.response(BUILD_INFO));
    when(igorService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME))
        .thenReturn(Calls.response(PROPERTIES));

    Function<Trigger, Trigger> triggerBuilder = eventHandler.buildTrigger(event);
    Trigger outputTrigger = triggerBuilder.apply(trigger);

    verify(igorService, times(1)).getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME);
    verify(igorService, times(1))
        .getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME);
    assertThat(outputTrigger.getBuildInfo()).isEqualTo(BUILD_INFO);
    assertThat(outputTrigger.getProperties()).isEqualTo(PROPERTIES);
  }

  @Test
  void fetchesPropertyFileIfDefinedWithJobNameFromQueryWhenFlagIsTrue() {
    Trigger trigger =
        enabledJenkinsTrigger()
            .withMaster(MASTER_NAME)
            .withBuildNumber(BUILD_NUMBER)
            .withPropertyFile(PROPERTY_FILE);
    BuildEvent event = getBuildEvent();

    RetrySupport retrySupport = new RetrySupport();
    IgorConfigurationProperties configProperties = new IgorConfigurationProperties();
    configProperties.setJobNameAsQueryParameter(true);
    BuildInfoService buildInfoService =
        new BuildInfoService(igorService, retrySupport, configProperties);
    BuildEventHandler buildEventHandler =
        new BuildEventHandler(
            registry, objectMapper, Optional.of(buildInfoService), fiatPermissionEvaluator);

    when(igorService.getBuildStatusWithJobQueryParameter(BUILD_NUMBER, MASTER_NAME, JOB_NAME))
        .thenReturn(Calls.response(BUILD_INFO));
    when(igorService.getPropertyFileWithJobQueryParameter(
            BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME))
        .thenReturn(Calls.response(PROPERTIES));

    Function<Trigger, Trigger> triggerBuilder = buildEventHandler.buildTrigger(event);
    Trigger outputTrigger = triggerBuilder.apply(trigger);

    verify(igorService, times(1))
        .getBuildStatusWithJobQueryParameter(BUILD_NUMBER, MASTER_NAME, JOB_NAME);
    verify(igorService, times(1))
        .getPropertyFileWithJobQueryParameter(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME);
    assertThat(outputTrigger.getBuildInfo()).isEqualTo(BUILD_INFO);
    assertThat(outputTrigger.getProperties()).isEqualTo(PROPERTIES);
  }

  @Test
  void checksConstraintsOnPropertyFileIfDefined() {
    Trigger trigger =
        enabledJenkinsTrigger()
            .withMaster(MASTER_NAME)
            .withJob(JOB_NAME)
            .withBuildNumber(BUILD_NUMBER)
            .withPropertyFile(PROPERTY_FILE)
            .withPayloadConstraints(CONSTRAINTS);
    BuildEvent event = getBuildEvent();

    when(igorService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME))
        .thenReturn(Calls.response(PROPERTIES));

    Predicate<Trigger> matchTriggerPredicate = eventHandler.matchTriggerFor(event);
    boolean result = matchTriggerPredicate.test(trigger);

    verify(igorService, times(1))
        .getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME);
    assertThat(result).isTrue();
  }

  @Test
  void retriesOnFailureToCommunicateWithIgor() {
    Trigger trigger =
        enabledJenkinsTrigger()
            .withMaster(MASTER_NAME)
            .withJob(JOB_NAME)
            .withBuildNumber(BUILD_NUMBER)
            .withPropertyFile(PROPERTY_FILE);
    BuildEvent event = getBuildEvent();

    when(igorService.getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME))
        .thenThrow(new RuntimeException())
        .thenReturn(Calls.response(BUILD_INFO));
    when(igorService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME))
        .thenReturn(Calls.response(PROPERTIES));

    Function<Trigger, Trigger> triggerBuilder = eventHandler.buildTrigger(event);
    Trigger outputTrigger = triggerBuilder.apply(trigger);

    verify(igorService, times(2)).getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME);
    verify(igorService, times(1))
        .getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME);
    assertThat(outputTrigger.getBuildInfo()).isEqualTo(BUILD_INFO);
    assertThat(outputTrigger.getProperties()).isEqualTo(PROPERTIES);
  }

  private static Stream<Arguments> permissionParams() {
    return Stream.of(
        Arguments.of(enabledConcourseTrigger(), false, "should not", "does not have"),
        Arguments.of(enabledJenkinsTriggerWithRunAsUser(), true, "should", "has"));
  }

  @ParameterizedTest(name = "{2} trigger a pipeline if the user {3} access to the application")
  @MethodSource("permissionParams")
  void triggersPipelineBasedOnUserAccessToApplication(
      Trigger trigger, boolean hasPermission, String description1, String description2)
      throws TimeoutException {
    Pipeline pipeline =
        Pipeline.builder()
            .application("application")
            .name("pipeline")
            .id("id")
            .triggers(List.of(trigger))
            .build();
    PipelineCache cache = handlerSupport.pipelineCache(pipeline);
    BuildEvent event = createBuildEventWith(BuildEvent.Result.SUCCESS);

    String expectedUser =
        (trigger.getRunAsUser() == null || trigger.getRunAsUser().isBlank())
            ? "anonymous"
            : trigger.getRunAsUser();

    FiatPermissionEvaluator perTestFiatPermissionEvaluator = mock(FiatPermissionEvaluator.class);
    when(perTestFiatPermissionEvaluator.hasPermission(
            eq(expectedUser), eq("application"), eq("APPLICATION"), eq("EXECUTE")))
        .thenReturn(hasPermission);
    BuildEventHandler buildEventHandler =
        new BuildEventHandler(
            registry, objectMapper, Optional.of(buildInformation), perTestFiatPermissionEvaluator);

    List<Pipeline> matchingPipelines = buildEventHandler.getMatchingPipelines(event, cache);

    verify(perTestFiatPermissionEvaluator, times(1))
        .hasPermission(eq(expectedUser), eq("application"), eq("APPLICATION"), eq("EXECUTE"));
    assertThat(matchingPipelines).hasSize(hasPermission ? 1 : 0);
  }
}
