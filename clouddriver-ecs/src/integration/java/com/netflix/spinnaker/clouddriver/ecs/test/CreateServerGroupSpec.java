/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsSpec;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.util.Collections;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

public class CreateServerGroupSpec extends EcsSpec {

  @Autowired AccountCredentialsRepository accountCredentialsRepository;

  @DisplayName(
      ".\n===\n"
          + "Given description w/ inputs, EC2 launch type, and legacy target group fields, "
          + "successfully submit createServerGroup operation"
          + "\n===")
  @Test
  public void createServerGroupOperationTest() throws IOException {
    /**
     * TODO (allisaurus): Ideally this test would go further and actually assert that the resulting
     * ecs:create-service call is formed as expected, but for now, it asserts that the given
     * operation is correctly validated and submitted as a task.
     */

    // given
    String url = getTestUrl("/ecs/ops/createServerGroup");
    String requestBody = generateStringFromTestFile("/createServerGroup-inputs-ec2.json");
    setEcsAccountCreds();

    // when
    given()
        .contentType(ContentType.JSON)
        .body(requestBody)
        .when()
        .post(url)
        // then
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("id", notNullValue())
        .body("resourceUri", containsString("/task/"));
  }

  private void setEcsAccountCreds() {
    AmazonCredentials.AWSRegion testRegion = new AmazonCredentials.AWSRegion(TEST_REGION, null);

    NetflixAmazonCredentials ecsCreds =
        new NetflixAmazonCredentials(
            ECS_ACCOUNT_NAME,
            "test",
            "test",
            "123456789012",
            null,
            true,
            Collections.singletonList(testRegion),
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            false);

    accountCredentialsRepository.save(ECS_ACCOUNT_NAME, ecsCreds);
  }
}
