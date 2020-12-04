/*
 * Copyright 2020 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.test;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class CreateServerGroupWithMonikerSpec extends EcsSpec {

  private AmazonECS mockECS = mock(AmazonECS.class);
  private AmazonElasticLoadBalancing mockELB = mock(AmazonElasticLoadBalancing.class);

  @BeforeEach
  public void setup() {
    when(mockECS.listServices(any(ListServicesRequest.class))).thenReturn(new ListServicesResult());
    when(mockECS.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(new DescribeServicesResult());
    when(mockECS.registerTaskDefinition(any(RegisterTaskDefinitionRequest.class)))
        .thenAnswer(
            (Answer<RegisterTaskDefinitionResult>)
                invocation -> {
                  RegisterTaskDefinitionRequest request =
                      (RegisterTaskDefinitionRequest) invocation.getArguments()[0];
                  String testArn = "arn:aws:ecs:::task-definition/" + request.getFamily() + ":1";
                  TaskDefinition taskDef = new TaskDefinition().withTaskDefinitionArn(testArn);
                  return new RegisterTaskDefinitionResult().withTaskDefinition(taskDef);
                });
    when(mockECS.createService(any(CreateServiceRequest.class)))
        .thenReturn(
            new CreateServiceResult().withService(new Service().withServiceName("createdService")));

    when(mockAwsProvider.getAmazonEcs(
            any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(mockECS);
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ inputs, EC2 launch type, and moniker enabled "
          + "successfully submit createServerGroup operation with tags"
          + "\n===")
  @Test
  public void createServerGroup_InputsEc2WithMoniker() throws IOException, InterruptedException {
    // When account has tags enabled
    when(mockECS.listAccountSettings(any(ListAccountSettingsRequest.class)))
        .thenReturn(
            new ListAccountSettingsResult()
                .withSettings(
                    new Setting().withName(SettingName.ServiceLongArnFormat).withValue("enabled"),
                    new Setting().withName(SettingName.TaskLongArnFormat).withValue("enabled")));

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody = generateStringFromTestFile("/createServerGroup-inputs-ec2-moniker.json");
    String expectedServerGroupName = "ecs-integInputsMoniker-detailTest";

    // when
    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(url)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    retryUntilTrue(
        () -> {
          List<Object> taskHistory =
              get(getTestUrl("/task/" + taskId))
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("history");
          if (taskHistory
              .toString()
              .contains(String.format("Done creating 1 of %s-v000", expectedServerGroupName))) {
            return true;
          }
          return false;
        },
        String.format("Failed to detect service creation in %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);

    // then
    ArgumentCaptor<RegisterTaskDefinitionRequest> registerTaskDefArgs =
        ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(mockECS).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.getFamily());
    assertEquals(1, seenTaskDefRequest.getContainerDefinitions().size());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockECS).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.getLaunchType());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.getServiceName());
    assertEquals(4, seenCreateServRequest.getTags().size());
    assertThat(
        seenCreateServRequest.getTags(),
        containsInAnyOrder(
            new Tag().withKey("moniker.spinnaker.io/application").withValue("ecs"),
            new Tag().withKey("moniker.spinnaker.io/stack").withValue("integInputsMoniker"),
            new Tag().withKey("moniker.spinnaker.io/detail").withValue("detailTest"),
            new Tag().withKey("moniker.spinnaker.io/sequence").withValue("0")));
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ inputs, EC2 launch type, and moniker enabled "
          + "task should fail if ECS account has tags disabled"
          + "\n===")
  @Test
  public void createServerGroup_errorIfCreateServiceFails()
      throws IOException, InterruptedException {
    // When account has tags disabled
    when(mockECS.listAccountSettings(any(ListAccountSettingsRequest.class)))
        .thenReturn(new ListAccountSettingsResult());

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody = generateStringFromTestFile("/createServerGroup-inputs-ec2-moniker.json");

    // when
    Mockito.doThrow(new InvalidParameterException("Something is wrong."))
        .when(mockECS)
        .createService(any(CreateServiceRequest.class));

    String taskId =
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(url)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("resourceUri", containsString("/task/"))
            .extract()
            .path("id");

    // then
    retryUntilTrue(
        () -> {
          HashMap<String, Boolean> status =
              get(getTestUrl("/task/" + taskId))
                  .then()
                  .contentType(ContentType.JSON)
                  .extract()
                  .path("status");

          return status.get("failed").equals(true);
        },
        String.format("Failed to observe task failure after %s seconds", TASK_RETRY_SECONDS),
        TASK_RETRY_SECONDS);
  }
}
