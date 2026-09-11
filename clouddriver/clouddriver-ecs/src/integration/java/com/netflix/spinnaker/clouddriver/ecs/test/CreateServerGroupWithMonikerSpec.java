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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;

public class CreateServerGroupWithMonikerSpec extends EcsSpec {

  private EcsClient mockEcsV2 = mock(EcsClient.class);

  @BeforeEach
  public void setup() {
    // mock v2 ECS responses (used by EcsServerGroupNameResolver)
    when(mockEcsV2.listServices(any(ListServicesRequest.class)))
        .thenReturn(
            ListServicesResponse.builder().serviceArns(java.util.Collections.emptyList()).build());
    when(mockEcsV2.describeServices(any(DescribeServicesRequest.class)))
        .thenReturn(
            DescribeServicesResponse.builder().services(java.util.Collections.emptyList()).build());

    when(mockEcsV2.registerTaskDefinition(any(RegisterTaskDefinitionRequest.class)))
        .thenAnswer(
            (Answer<RegisterTaskDefinitionResponse>)
                invocation -> {
                  RegisterTaskDefinitionRequest request = invocation.getArgument(0);
                  String testArn = "arn:aws:ecs:::task-definition/" + request.family() + ":1";
                  return RegisterTaskDefinitionResponse.builder()
                      .taskDefinition(TaskDefinition.builder().taskDefinitionArn(testArn).build())
                      .build();
                });
    when(mockEcsV2.createService(any(CreateServiceRequest.class)))
        .thenReturn(
            CreateServiceResponse.builder()
                .service(Service.builder().serviceName("createdService").build())
                .build());

    when(mockAwsProvider.getAmazonEcsV2(any(NetflixAmazonCredentials.class), anyString()))
        .thenReturn(mockEcsV2);
  }

  @DisplayName(
      ".\n===\n"
          + "Given description w/ inputs, EC2 launch type, and moniker enabled "
          + "successfully submit createServerGroup operation with tags"
          + "\n===")
  @Test
  public void createServerGroup_InputsEc2WithMoniker() throws IOException, InterruptedException {
    // When account has tags enabled
    when(mockEcsV2.listAccountSettings(any(ListAccountSettingsRequest.class)))
        .thenReturn(
            ListAccountSettingsResponse.builder()
                .settings(
                    Setting.builder()
                        .name(SettingName.SERVICE_LONG_ARN_FORMAT)
                        .value("enabled")
                        .build(),
                    Setting.builder()
                        .name(SettingName.TASK_LONG_ARN_FORMAT)
                        .value("enabled")
                        .build())
                .build());

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
    verify(mockEcsV2).registerTaskDefinition(registerTaskDefArgs.capture());
    RegisterTaskDefinitionRequest seenTaskDefRequest = registerTaskDefArgs.getValue();
    assertEquals(expectedServerGroupName, seenTaskDefRequest.family());
    assertEquals(1, seenTaskDefRequest.containerDefinitions().size());

    ArgumentCaptor<CreateServiceRequest> createServiceArgs =
        ArgumentCaptor.forClass(CreateServiceRequest.class);
    verify(mockEcsV2).createService(createServiceArgs.capture());
    CreateServiceRequest seenCreateServRequest = createServiceArgs.getValue();
    assertEquals("EC2", seenCreateServRequest.launchTypeAsString());
    assertEquals(expectedServerGroupName + "-v000", seenCreateServRequest.serviceName());
    assertEquals(4, seenCreateServRequest.tags().size());
    assertThat(
        seenCreateServRequest.tags(),
        containsInAnyOrder(
            Tag.builder().key("moniker.spinnaker.io/application").value("ecs").build(),
            Tag.builder().key("moniker.spinnaker.io/stack").value("integInputsMoniker").build(),
            Tag.builder().key("moniker.spinnaker.io/detail").value("detailTest").build(),
            Tag.builder().key("moniker.spinnaker.io/sequence").value("0").build()));
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
    when(mockEcsV2.listAccountSettings(any(ListAccountSettingsRequest.class)))
        .thenReturn(ListAccountSettingsResponse.builder().build());

    // given
    String url = getTestUrl(CREATE_SG_TEST_PATH);
    String requestBody = generateStringFromTestFile("/createServerGroup-inputs-ec2-moniker.json");

    // when
    Mockito.doThrow(InvalidParameterException.builder().message("Something is wrong.").build())
        .when(mockEcsV2)
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
