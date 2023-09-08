/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.kubernetes.it;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.hasItems;

import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.kubernetes.it.containers.KubernetesCluster;
import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.TestLifecycleListener;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {Main.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location = classpath:clouddriver.yml"})
@ExtendWith(TestLifecycleListener.class)
public abstract class BaseTest {

  public static final String APP1_NAME = "testApp1";
  public static final String APP2_NAME = "testApp2";
  public static final String ACCOUNT1_NAME = "account1";
  public static final String ACCOUNT2_NAME = "account2";
  public static final String KUBERNETES_VERSION = System.getenv("KUBERNETES_VERSION");

  @LocalServerPort int port;

  public static final KubernetesCluster kubeCluster;

  static {
    kubeCluster = KubernetesCluster.getInstance();
    kubeCluster.start();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  public String baseUrl() {
    return "http://localhost:" + port;
  }

  @BeforeEach
  void givenAccountsReady() {
    Response response = get(baseUrl() + "/credentials");
    response
        .then()
        .log()
        .ifValidationFails()
        .assertThat()
        .statusCode(200)
        .and()
        .body("name", hasItems(ACCOUNT1_NAME));
  }
}
