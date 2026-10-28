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

package com.netflix.spinnaker.clouddriver.ecs.deploy.validators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeServiceTag;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeCreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeUpdateServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AnnotationsBasedAtomicOperationsRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class EcsNativeDescriptionValidationTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 50, 100})
  void nativeCreateAcceptsMinimumHealthyPercentInEcsRange(int minimumHealthyPercent) {
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setMinimumHealthyPercent(minimumHealthyPercent);

    assertTrue(!errorFields(createValidator(), description).contains("minimumHealthyPercent"));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void nativeCreateRejectsMinimumHealthyPercentOutsideEcsRange(int minimumHealthyPercent) {
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setMinimumHealthyPercent(minimumHealthyPercent);

    assertTrue(errorFields(createValidator(), description).contains("minimumHealthyPercent"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 50, 100})
  void nativeUpdateAcceptsMinimumHealthyPercentInEcsRange(int minimumHealthyPercent) {
    EcsNativeUpdateServiceDescription description = new EcsNativeUpdateServiceDescription();
    description.setMinimumHealthyPercent(minimumHealthyPercent);

    assertTrue(!errorFields(updateValidator(), description).contains("minimumHealthyPercent"));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void nativeUpdateRejectsMinimumHealthyPercentOutsideEcsRange(int minimumHealthyPercent) {
    EcsNativeUpdateServiceDescription description = new EcsNativeUpdateServiceDescription();
    description.setMinimumHealthyPercent(minimumHealthyPercent);

    assertTrue(errorFields(updateValidator(), description).contains("minimumHealthyPercent"));
  }

  @Test
  void createAndUpdateShareNativeSettingErrors() {
    EcsNativeCreateServerGroupDescription create = new EcsNativeCreateServerGroupDescription();
    EcsNativeUpdateServiceDescription update = new EcsNativeUpdateServiceDescription();
    populateInvalidSettings(create);
    populateInvalidSettings(update);

    DescriptionValidationErrors createErrors = validate(createValidator(), create);
    DescriptionValidationErrors updateErrors = validate(updateValidator(), update);

    List<String> expectedFields =
        List.of(
            "minimumHealthyPercent",
            "maximumPercent",
            "bakeTimeInMinutes",
            "deploymentStrategy",
            "alarmNames");
    for (String field : expectedFields) {
      assertEquals(
          "createServerGroupDescription." + field + "." + reason(field),
          codeFor(createErrors, field));
      assertEquals(
          "updateServiceAndTaskConfigDescription." + field + "." + reason(field),
          codeFor(updateErrors, field));
    }
  }

  @Test
  void nativeCreateAcceptsBlueGreenLifecycle() {
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setDeploymentStrategy("BLUE_GREEN");

    assertTrue(!errorFields(createValidator(), description).contains("deploymentStrategy"));
  }

  @Test
  void nativeUpdateAcceptsExplicitBlueGreen() {
    // An explicit BLUE_GREEN must behave like one inherited from the service: the update keeps the
    // service's ALB traffic-shift config, so both are valid.
    EcsNativeUpdateServiceDescription description = new EcsNativeUpdateServiceDescription();
    description.setDeploymentStrategy("BLUE_GREEN");

    assertTrue(!errorFields(updateValidator(), description).contains("deploymentStrategy"));
  }

  @Test
  void nativeCreateRequiresTrafficShiftConfigForBlueGreenOnLoadBalancedDeploy() {
    // BLUE_GREEN plus a target group but none of the four ALB fields: ECS would reject the deploy
    // once the request reaches it, so the validator must fail it before the operation runs (and
    // before a task-definition revision is registered).
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setDeploymentStrategy("blue_green");
    description.setTargetGroup("arn:target-group");

    assertEquals(
        "createServerGroupDescription.alternateTargetGroupArn.required",
        codeFor(validate(createValidator(), description), "alternateTargetGroupArn"));
  }

  @Test
  void nativeCreateAcceptsBlueGreenWithoutLoadBalancerAndWithoutTrafficShiftConfig() {
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setDeploymentStrategy("BLUE_GREEN");

    assertFalse(errorFields(createValidator(), description).contains("alternateTargetGroupArn"));
  }

  @Test
  void nativeCreateAcceptsBlueGreenWithCompleteTrafficShiftConfig() {
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setDeploymentStrategy("BLUE_GREEN");
    description.setTargetGroup("arn:target-group");
    description.setAlternateTargetGroupArn("arn:alternate-target-group");
    description.setProductionListenerRule("arn:production-rule");
    description.setTestListenerRule("arn:test-rule");
    description.setBlueGreenRoleArn("arn:aws:iam::123456789012:role/ecsBlueGreenRole");

    assertFalse(errorFields(createValidator(), description).contains("alternateTargetGroupArn"));
  }

  @Test
  void nativeCreateRejectsPartialTrafficShiftConfigOnLoadBalancedDeploy() {
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setTargetGroup("arn:target-group");
    description.setAlternateTargetGroupArn("arn:alternate-target-group");

    assertEquals(
        "createServerGroupDescription.alternateTargetGroupArn.incomplete",
        codeFor(validate(createValidator(), description), "alternateTargetGroupArn"));
  }

  @Test
  void nativeCreateDoesNotRejectTheReservedOwnershipTag() {
    // The native create operation strips and re-adds the canonical ownership tag itself, so a
    // caller-supplied value is normalized rather than rejected (unlike a classic deploy).
    EcsNativeCreateServerGroupDescription description = new EcsNativeCreateServerGroupDescription();
    description.setTags(Map.of(EcsNativeServiceTag.KEY, "something-else"));

    assertFalse(errorFields(createValidator(), description).contains("tags"));
  }

  private static void populateInvalidSettings(EcsNativeCreateServerGroupDescription description) {
    description.setMinimumHealthyPercent(-1);
    description.setMaximumPercent(99);
    description.setBakeTimeInMinutes(-1);
    description.setDeploymentStrategy("CANARY");
    description.setEnableDeploymentAlarms(true);
  }

  private static void populateInvalidSettings(EcsNativeUpdateServiceDescription description) {
    description.setMinimumHealthyPercent(-1);
    description.setMaximumPercent(99);
    description.setBakeTimeInMinutes(-1);
    description.setDeploymentStrategy("CANARY");
    description.setEnableDeploymentAlarms(true);
  }

  private static String reason(String field) {
    return "alarmNames".equals(field) ? "not.nullable" : "invalid";
  }

  private static String codeFor(DescriptionValidationErrors errors, String field) {
    return errors.getFieldErrors().stream()
        .filter(error -> field.equals(error.getField()))
        .map(error -> error.getCode())
        .findFirst()
        .orElse(null);
  }

  private static List<String> errorFields(DescriptionValidator validator, Object description) {
    return validate(validator, description).getFieldErrors().stream()
        .map(error -> error.getField())
        .collect(Collectors.toList());
  }

  private static DescriptionValidationErrors validate(
      DescriptionValidator validator, Object description) {
    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);
    validator.validate(Collections.emptyList(), description, errors);
    return errors;
  }

  private static DescriptionValidator createValidator() {
    return nativeValidator(
        EcsCreateServerGroupDescriptionValidator.class, AtomicOperations.CREATE_SERVER_GROUP);
  }

  private static DescriptionValidator updateValidator() {
    return nativeValidator(
        UpdateServiceAndTaskConfigAtomicOperationValidator.class,
        AtomicOperations.UPDATE_LAUNCH_CONFIG);
  }

  /** Resolves the validator the way clouddriver does for an ecs-native operation. */
  private static DescriptionValidator nativeValidator(
      Class<? extends DescriptionValidator> validatorClass, String operation) {
    AnnotationConfigApplicationContext applicationContext =
        new AnnotationConfigApplicationContext();
    applicationContext.registerBean(validatorClass);
    applicationContext.refresh();

    AnnotationsBasedAtomicOperationsRegistry registry =
        new AnnotationsBasedAtomicOperationsRegistry();
    ReflectionTestUtils.setField(registry, "applicationContext", applicationContext);
    ReflectionTestUtils.setField(
        registry, "cloudProviders", Collections.singletonList(new EcsNativeCloudProvider()));

    DescriptionValidator validator =
        registry.getAtomicOperationDescriptionValidator(
            DescriptionValidator.getValidatorName(operation), EcsNativeCloudProvider.ID);
    assertNotNull(validator);
    return validator;
  }
}
