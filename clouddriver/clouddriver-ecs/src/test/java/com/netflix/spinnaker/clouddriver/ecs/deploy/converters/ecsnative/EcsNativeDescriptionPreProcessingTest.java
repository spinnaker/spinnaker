/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.converters.ecsnative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CloneServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DeleteScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeCreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeServiceDeploymentDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeUpdateServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.StartServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.StopServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.TerminateInstancesDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.UpsertScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AnnotationsBasedAtomicOperationsRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends each ecs-native operation through {@link OperationsService} and checks the description type
 * handed to the description pre-processors. The ecs-native converters inherit a generic {@code D
 * convertDescription(Map)}; when that type was not resolved, pre-processors were called with {@code
 * null} and the AWS {@code AllowLaunchPreProcessor} failed every ecs-native operation with a
 * NullPointerException.
 */
class EcsNativeDescriptionPreProcessingTest {

  private static final List<Class<? extends AtomicOperationConverter>> CONVERTERS =
      List.of(
          EcsNativeCloneServiceAtomicOperationConverter.class,
          EcsNativeContinueServiceDeploymentAtomicOperationConverter.class,
          EcsNativeCreateServerGroupAtomicOperationConverter.class,
          EcsNativeDeleteScalingPolicyAtomicOperationConverter.class,
          EcsNativeDestroyServiceAtomicOperationConverter.class,
          EcsNativeDisableServiceAtomicOperationConverter.class,
          EcsNativeEnableServiceAtomicOperationConverter.class,
          EcsNativeResizeServiceAtomicOperationConverter.class,
          EcsNativeStartServiceAtomicOperationConverter.class,
          EcsNativeStopServiceAtomicOperationConverter.class,
          EcsNativeStopServiceDeploymentAtomicOperationConverter.class,
          EcsNativeTerminateInstancesAtomicOperationConverter.class,
          EcsNativeUpdateServiceAtomicOperationConverter.class,
          EcsNativeUpsertScalingPolicyAtomicOperationConverter.class);

  private AnnotationConfigApplicationContext applicationContext;
  private RecordingPreProcessor preProcessor;
  private OperationsService operationsService;

  @BeforeEach
  void setUp() {
    applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.registerBean(ObjectMapper.class);
    applicationContext.registerBean(
        AccountCredentialsProvider.class, () -> mock(AccountCredentialsProvider.class));
    CONVERTERS.forEach(converter -> applicationContext.registerBean(converter));
    applicationContext.refresh();

    AnnotationsBasedAtomicOperationsRegistry registry =
        new AnnotationsBasedAtomicOperationsRegistry();
    ReflectionTestUtils.setField(registry, "applicationContext", applicationContext);
    ReflectionTestUtils.setField(registry, "cloudProviders", List.of(new EcsNativeCloudProvider()));

    preProcessor = new RecordingPreProcessor();
    operationsService =
        new OperationsService(
            registry,
            List.of(),
            Optional.empty(),
            Optional.of(List.of(preProcessor)),
            mock(AccountCredentialsRepository.class),
            Optional.empty(),
            new NoopRegistry(),
            JsonMapper.builder().build(),
            mock(ExceptionMessageDecorator.class));
  }

  @AfterEach
  void tearDown() {
    applicationContext.close();
  }

  static Stream<Arguments> operations() {
    return Stream.of(
        Arguments.of(AtomicOperations.CLONE_SERVER_GROUP, CloneServiceDescription.class),
        Arguments.of("continueServiceDeployment", EcsNativeServiceDeploymentDescription.class),
        Arguments.of(
            AtomicOperations.CREATE_SERVER_GROUP, EcsNativeCreateServerGroupDescription.class),
        Arguments.of(AtomicOperations.DELETE_SCALING_POLICY, DeleteScalingPolicyDescription.class),
        Arguments.of(AtomicOperations.DESTROY_SERVER_GROUP, ModifyServiceDescription.class),
        Arguments.of(AtomicOperations.DISABLE_SERVER_GROUP, ModifyServiceDescription.class),
        Arguments.of(AtomicOperations.ENABLE_SERVER_GROUP, ModifyServiceDescription.class),
        Arguments.of(AtomicOperations.RESIZE_SERVER_GROUP, ResizeServiceDescription.class),
        Arguments.of(AtomicOperations.START_SERVER_GROUP, StartServiceDescription.class),
        Arguments.of(AtomicOperations.STOP_SERVER_GROUP, StopServiceDescription.class),
        Arguments.of("stopServiceDeployment", EcsNativeServiceDeploymentDescription.class),
        Arguments.of(AtomicOperations.TERMINATE_INSTANCES, TerminateInstancesDescription.class),
        Arguments.of(
            AtomicOperations.UPDATE_LAUNCH_CONFIG, EcsNativeUpdateServiceDescription.class),
        Arguments.of(AtomicOperations.UPSERT_SCALING_POLICY, UpsertScalingPolicyDescription.class));
  }

  @ParameterizedTest
  @MethodSource("operations")
  void preProcessorsReceiveTheConcreteDescriptionType(
      String operation, Class<?> expectedDescriptionType) {
    // The pre-processor stops the conversion once it has seen the type, so the test needs no
    // credentials or AWS clients for the rest of the operation.
    assertThrows(
        StopConversion.class,
        () ->
            operationsService.collectAtomicOperations(
                EcsNativeCloudProvider.ID, List.of(Map.of(operation, Map.of()))));

    assertEquals(expectedDescriptionType, preProcessor.descriptionType);
  }

  private static class RecordingPreProcessor implements AtomicOperationDescriptionPreProcessor {
    private Class<?> descriptionType;

    @Override
    public boolean supports(Class descriptionClass) {
      descriptionType = descriptionClass;
      throw new StopConversion();
    }

    @Override
    public Map process(Map description) {
      return description;
    }
  }

  private static class StopConversion extends RuntimeException {}
}
