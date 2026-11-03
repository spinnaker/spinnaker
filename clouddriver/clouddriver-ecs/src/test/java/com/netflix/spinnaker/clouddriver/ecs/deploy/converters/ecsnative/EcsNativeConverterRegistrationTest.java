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
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.CloneServiceAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.DeleteScalingPolicyAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.DestroyServiceAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.DisableServiceAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.EcsCreateServerGroupAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.EnableServiceAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.ResizeServiceAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.StartServiceAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.StopServiceAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.TerminateInstancesAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.UpdateServiceAndTaskConfigAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.ecs.deploy.converters.UpsertScalingPolicyAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.orchestration.AnnotationsBasedAtomicOperationsRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Proves that, with both providers' converters registered, the operation registry resolves exactly
 * one converter per (operation, provider): the ecs-native subclasses for {@code ecs-native}, the
 * classic converters for {@code ecs}, and that the unannotated abstract ecs-native bases add no
 * extra match (the registry fails on more than one).
 */
class EcsNativeConverterRegistrationTest {

  private static final Map<String, List<Class<? extends AtomicOperationConverter>>> CONVERTERS =
      Map.ofEntries(
          Map.entry(
              AtomicOperations.CREATE_SERVER_GROUP,
              List.of(
                  EcsNativeCreateServerGroupAtomicOperationConverter.class,
                  EcsCreateServerGroupAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.CLONE_SERVER_GROUP,
              List.of(
                  EcsNativeCloneServiceAtomicOperationConverter.class,
                  CloneServiceAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.DELETE_SCALING_POLICY,
              List.of(
                  EcsNativeDeleteScalingPolicyAtomicOperationConverter.class,
                  DeleteScalingPolicyAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.DESTROY_SERVER_GROUP,
              List.of(
                  EcsNativeDestroyServiceAtomicOperationConverter.class,
                  DestroyServiceAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.DISABLE_SERVER_GROUP,
              List.of(
                  EcsNativeDisableServiceAtomicOperationConverter.class,
                  DisableServiceAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.ENABLE_SERVER_GROUP,
              List.of(
                  EcsNativeEnableServiceAtomicOperationConverter.class,
                  EnableServiceAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.RESIZE_SERVER_GROUP,
              List.of(
                  EcsNativeResizeServiceAtomicOperationConverter.class,
                  ResizeServiceAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.START_SERVER_GROUP,
              List.of(
                  EcsNativeStartServiceAtomicOperationConverter.class,
                  StartServiceAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.STOP_SERVER_GROUP,
              List.of(
                  EcsNativeStopServiceAtomicOperationConverter.class,
                  StopServiceAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.TERMINATE_INSTANCES,
              List.of(
                  EcsNativeTerminateInstancesAtomicOperationConverter.class,
                  TerminateInstancesAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.UPDATE_LAUNCH_CONFIG,
              List.of(
                  EcsNativeUpdateServiceAtomicOperationConverter.class,
                  UpdateServiceAndTaskConfigAtomicOperationConverter.class)),
          Map.entry(
              AtomicOperations.UPSERT_SCALING_POLICY,
              List.of(
                  EcsNativeUpsertScalingPolicyAtomicOperationConverter.class,
                  UpsertScalingPolicyAtomicOperationConverter.class)));

  private AnnotationConfigApplicationContext applicationContext;
  private AnnotationsBasedAtomicOperationsRegistry registry;

  @BeforeEach
  void setUp() {
    applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.registerBean(ObjectMapper.class);
    applicationContext.registerBean(
        AccountCredentialsProvider.class, () -> mock(AccountCredentialsProvider.class));
    CONVERTERS.values().stream()
        .flatMap(List::stream)
        .forEach(converter -> applicationContext.registerBean(converter));
    applicationContext.registerBean(
        EcsNativeContinueServiceDeploymentAtomicOperationConverter.class);
    applicationContext.registerBean(EcsNativeStopServiceDeploymentAtomicOperationConverter.class);
    applicationContext.refresh();

    registry = new AnnotationsBasedAtomicOperationsRegistry();
    ReflectionTestUtils.setField(registry, "applicationContext", applicationContext);
    ReflectionTestUtils.setField(
        registry, "cloudProviders", List.of(new EcsCloudProvider(), new EcsNativeCloudProvider()));
  }

  @AfterEach
  void tearDown() {
    applicationContext.close();
  }

  static Stream<Arguments> sharedOperations() {
    return CONVERTERS.entrySet().stream()
        .map(
            entry ->
                Arguments.of(entry.getKey(), entry.getValue().get(0), entry.getValue().get(1)));
  }

  @ParameterizedTest
  @MethodSource("sharedOperations")
  void resolvesExactlyOneConverterPerProvider(
      String operation,
      Class<? extends AtomicOperationConverter> nativeConverter,
      Class<? extends AtomicOperationConverter> classicConverter) {
    assertEquals(
        nativeConverter,
        registry.getAtomicOperationConverter(operation, EcsNativeCloudProvider.ID).getClass());
    assertEquals(
        classicConverter,
        registry.getAtomicOperationConverter(operation, EcsCloudProvider.ID).getClass());
  }

  @ParameterizedTest
  @MethodSource("nativeOnlyOperations")
  void resolvesNativeOnlyLifecycleOperations(
      String operation, Class<? extends AtomicOperationConverter> nativeConverter) {
    assertEquals(
        nativeConverter,
        registry.getAtomicOperationConverter(operation, EcsNativeCloudProvider.ID).getClass());
  }

  static Stream<Arguments> nativeOnlyOperations() {
    return Stream.of(
        Arguments.of(
            "continueServiceDeployment",
            EcsNativeContinueServiceDeploymentAtomicOperationConverter.class),
        Arguments.of(
            "stopServiceDeployment", EcsNativeStopServiceDeploymentAtomicOperationConverter.class));
  }
}
