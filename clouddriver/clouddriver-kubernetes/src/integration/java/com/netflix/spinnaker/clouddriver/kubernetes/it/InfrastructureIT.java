/*
 * Copyright 2021 Armory
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.it;

import static io.restassured.RestAssured.get;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Splitter;
import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.KubeTestUtils;
import io.restassured.response.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class InfrastructureIT extends BaseTest {

  private static final int CACHE_TIMEOUT_MIN = 5;
  private static final String DEPLOYMENT_1_NAME = "deployment1";
  private static final String NETWORK_POLICY_1_NAME = "default-deny-ingress";
  private static final String NETWORK_POLICY_2_NAME = "default-deny-ingress-second";
  private static final String SERVICE_1_NAME = "service1";
  private static final String APP_SECURITY_GROUPS = "security-groups";
  private static final String APP_SERVER_GROUP_MGRS = "server-group-managers";
  private static final String APP_LOAD_BALANCERS = "load-balancers";
  private static String account1Ns;
  private static String account2Ns;

  @BeforeAll
  public static void setUpAll() throws IOException, InterruptedException {
    account1Ns = kubeCluster.createNamespace(ACCOUNT1_NAME);
    account2Ns = kubeCluster.createNamespace(ACCOUNT2_NAME);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes deployment made by spinnaker to two accounts\n"
          + "When sending get clusters request\n"
          + "Then both deployments should be returned\n===")
  @Test
  public void shouldGetClusters() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT2_NAME,
        account2Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url = baseUrl() + "/applications/" + APP_SERVER_GROUP_MGRS + "/clusters";
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          resp.then().statusCode(200);
          List<Object> clustersAcc1 = resp.jsonPath().getList(ACCOUNT1_NAME);
          List<Object> clustersAcc2 = resp.jsonPath().getList(ACCOUNT1_NAME);
          return clustersAcc1 != null
              && clustersAcc1.contains("deployment " + DEPLOYMENT_1_NAME)
              && clustersAcc2 != null
              && clustersAcc2.contains("deployment " + DEPLOYMENT_1_NAME);
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + DEPLOYMENT_1_NAME
            + "' cluster to return from GET /applications/"
            + APP_SERVER_GROUP_MGRS
            + "/clusters");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes deployment made by spinnaker to two accounts\n"
          + "When sending get clusters request for one account\n"
          + "Then only the desired account deployment should be returned\n===")
  @Test
  public void shouldGetClustersByAccount() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT2_NAME,
        account2Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl() + "/applications/" + APP_SERVER_GROUP_MGRS + "/clusters/" + ACCOUNT1_NAME;
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          Map<Object, Object> account1Map =
              resp.jsonPath()
                  .getMap(
                      "find { it.account == '"
                          + ACCOUNT1_NAME
                          + "' && it.name == 'deployment "
                          + DEPLOYMENT_1_NAME
                          + "'}");
          Map<Object, Object> account2Map =
              resp.jsonPath().getMap("find { it.account == '" + ACCOUNT2_NAME + "'}");
          return (account1Map != null && !account1Map.isEmpty())
              && (account2Map == null || account2Map.isEmpty());
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + DEPLOYMENT_1_NAME
            + "' cluster to return from GET /applications/"
            + APP_SERVER_GROUP_MGRS
            + "/clusters/"
            + ACCOUNT1_NAME);
  }

  @DisplayName(
      ".\n===\n"
          + "Given two kubernetes deployments made by spinnaker\n"
          + "When sending get clusters request for the deployment name and account\n"
          + "Then only the desired deployment should be returned\n===")
  @Test
  public void shouldGetClustersByAccountAndName() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        "other",
        APP_SERVER_GROUP_MGRS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/applications/"
                  + APP_SERVER_GROUP_MGRS
                  + "/clusters/"
                  + ACCOUNT1_NAME
                  + "/deployment "
                  + DEPLOYMENT_1_NAME
                  + "?expand=true";
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          Map<Object, Object> map =
              resp.jsonPath()
                  .getMap(
                      "find { it.accountName == '"
                          + ACCOUNT1_NAME
                          + "' && it.name == 'deployment "
                          + DEPLOYMENT_1_NAME
                          + "' && it.application == '"
                          + APP_SERVER_GROUP_MGRS
                          + "'}");
          return map != null && !map.isEmpty() && resp.jsonPath().getList("$").size() == 1;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + DEPLOYMENT_1_NAME
            + "' cluster to return from GET /applications/"
            + APP_SERVER_GROUP_MGRS
            + "/clusters/"
            + ACCOUNT1_NAME);
  }

  @DisplayName(
      ".\n===\n"
          + "Given two kubernetes deployments made by spinnaker\n"
          + "When sending get clusters request for the deployment name, account and type\n"
          + "Then only the desired deployment should be returned\n===")
  @Test
  public void shouldGetClustersByAccountNameAndType() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        "other",
        APP_SERVER_GROUP_MGRS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/applications/"
                  + APP_SERVER_GROUP_MGRS
                  + "/clusters/"
                  + ACCOUNT1_NAME
                  + "/deployment "
                  + DEPLOYMENT_1_NAME
                  + "/kubernetes?expand=true";
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("accountName").equals(ACCOUNT1_NAME)
              && resp.jsonPath().getString("name").equals("deployment " + DEPLOYMENT_1_NAME)
              && resp.jsonPath().getString("application").equals(APP_SERVER_GROUP_MGRS);
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + DEPLOYMENT_1_NAME
            + "' cluster to return from GET /applications/"
            + APP_SERVER_GROUP_MGRS
            + "/clusters/"
            + ACCOUNT1_NAME);
  }

  @DisplayName(
      ".\n===\n"
          + "Given one deployment associated with two replicasets\n"
          + "When sending get server groups request\n"
          + "Then two server groups should be returned\n===")
  @Test
  public void shouldGetServerGroups() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        "index.docker.io/library/alpine:3.11",
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        "index.docker.io/library/alpine:3.12",
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl() + "/applications/" + APP_SERVER_GROUP_MGRS + "/serverGroups?expand=true";
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          resp.then().statusCode(200);
          List<Object> list =
              resp.jsonPath()
                  .getList(
                      "findAll { it.account == '"
                          + ACCOUNT1_NAME
                          + "' && it.region == '"
                          + account1Ns
                          + "' && it.cluster == 'deployment "
                          + DEPLOYMENT_1_NAME
                          + "'}");
          return list != null && list.size() > 1;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for at least two server groups to be returned from GET /applications/{app}/serverGroups");
  }

  @DisplayName(
      ".\n===\n"
          + "Given one deployment associated with two applications\n"
          + "When sending get server groups request by the two application\n"
          + "Then two server groups should be returned\n===")
  @Test
  public void shouldGetServerGroupsForApplications() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP2_NAME,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl() + "/serverGroups?applications=" + APP_SERVER_GROUP_MGRS + "," + APP2_NAME;
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          resp.then().statusCode(200);
          List<Object> list =
              resp.jsonPath()
                  .getList(
                      "findAll { it.account == '"
                          + ACCOUNT1_NAME
                          + "' && it.region == '"
                          + account1Ns
                          + "'}");
          return list != null && list.size() > 1;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for at least two server groups to be returned from GET /serverGroups?applications");
  }

  @DisplayName(
      ".\n===\n"
          + "Given one deployment associated with two replicasets\n"
          + "When sending get server group request for account, region and name\n"
          + "Then only one server group should be returned\n===")
  @Test
  public void shouldGetServerGroupByMoniker() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        "index.docker.io/library/alpine:3.11",
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        "index.docker.io/library/alpine:3.12",
        kubeCluster);

    List<String> rsNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "get -n "
                        + account1Ns
                        + " rs -o=jsonpath='{.items[?(@.metadata.ownerReferences[*].name==\""
                        + DEPLOYMENT_1_NAME
                        + "\")].metadata.name}'"));
    assertTrue(rsNames.size() > 1, "Expected more than one replicaset deployed");

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/serverGroups/"
                  + ACCOUNT1_NAME
                  + "/"
                  + account1Ns
                  + "/replicaSet "
                  + rsNames.get(0);
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("name").equals("replicaSet " + rsNames.get(0));
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'replicaSet "
            + rsNames.get(0)
            + "' to return from GET /serverGroups/{account}/{region}/{name}");
  }

  @DisplayName(
      ".\n===\n"
          + "Given one deployment associated with two replicasets\n"
          + "When sending get server group request for application, account, region and name\n"
          + "Then only one server group should be returned\n===")
  @Test
  public void shouldGetServerGroupByApplication() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        "index.docker.io/library/alpine:3.11",
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        "index.docker.io/library/alpine:3.12",
        kubeCluster);

    List<String> rsNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "get -n "
                        + account1Ns
                        + " rs -o=jsonpath='{.items[?(@.metadata.ownerReferences[*].name==\""
                        + DEPLOYMENT_1_NAME
                        + "\")].metadata.name}'"));
    assertTrue(rsNames.size() > 1, "Expected more than one replicaset deployed");

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/applications/"
                  + APP_SERVER_GROUP_MGRS
                  + "/serverGroups/"
                  + ACCOUNT1_NAME
                  + "/"
                  + account1Ns
                  + "/replicaSet "
                  + rsNames.get(0);
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("name").equals("replicaSet " + rsNames.get(0));
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'replicaSet "
            + rsNames.get(0)
            + "' to return from GET /applications/{application}/serverGroups/{account}/{region}/{name}/");
  }

  @DisplayName(
      ".\n===\n"
          + "Given one deployment of two pods\n"
          + "When sending get instance request for application, region and name\n"
          + "Then only one pod should be returned\n===")
  @Test
  public void shouldGetInstanceByAccountRegionId() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("metadata.namespace", account1Ns)
            .withValue("spec.replicas", 2)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP_SERVER_GROUP_MGRS)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    List<String> allPodNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "get -n " + account1Ns + " pod -o=jsonpath='{.items[*].metadata.name}'"));
    List<String> podNames = new ArrayList<>();
    for (String name : allPodNames) {
      if (name.startsWith(DEPLOYMENT_1_NAME)) {
        podNames.add(name);
      }
    }
    assertFalse(podNames.isEmpty());

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/instances/"
                  + ACCOUNT1_NAME
                  + "/"
                  + account1Ns
                  + "/pod "
                  + podNames.get(0);
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("displayName").equals(podNames.get(0));
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'pod "
            + podNames.get(0)
            + "' to return from GET /instances/{account}/{region}/{name}/");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes deployment\n"
          + "When sending get instance logs request for application, region and name\n"
          + "Then the pod logs should be returned\n===")
  @Test
  public void shouldGetInstanceLogs() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        kubeCluster);

    List<String> allPodNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "get -n " + account1Ns + " pod -o=jsonpath='{.items[*].metadata.name}'"));
    List<String> podNames = new ArrayList<>();
    for (String name : allPodNames) {
      if (name.startsWith(DEPLOYMENT_1_NAME)) {
        podNames.add(name);
      }
    }
    assertFalse(podNames.isEmpty());

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/instances/"
                  + ACCOUNT1_NAME
                  + "/"
                  + account1Ns
                  + "/pod "
                  + podNames.get(0)
                  + "/console?provider=kubernetes";
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("output[0].output") != null;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for logs of pod "
            + podNames.get(0)
            + " to return from GET /instances/{account}/{region}/{name}/console");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes service deployed by spinnaker to two accounts\n"
          + "When sending get load balancers request\n"
          + "Then both services should be returned\n===")
  @Test
  public void shouldGetLoadBalancers() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_LOAD_BALANCERS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "service",
        SERVICE_1_NAME,
        APP_LOAD_BALANCERS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT2_NAME,
        account2Ns,
        "service",
        SERVICE_1_NAME,
        APP_LOAD_BALANCERS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url = baseUrl() + "/applications/" + APP_LOAD_BALANCERS + "/loadBalancers";
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          resp.then().statusCode(200);
          Map<Object, Object> lbAcc1 =
              resp.jsonPath()
                  .getMap(
                      "find { it.account == '"
                          + ACCOUNT1_NAME
                          + "' && it.name == 'service "
                          + SERVICE_1_NAME
                          + "' && it.namespace == '"
                          + account1Ns
                          + "'}");
          Map<Object, Object> lbAcc2 =
              resp.jsonPath()
                  .getMap(
                      "find { it.account == '"
                          + ACCOUNT2_NAME
                          + "' && it.name == 'service "
                          + SERVICE_1_NAME
                          + "' && it.namespace == '"
                          + account2Ns
                          + "'}");
          return lbAcc1 != null && !lbAcc1.isEmpty() && lbAcc2 != null && !lbAcc2.isEmpty();
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'service "
            + SERVICE_1_NAME
            + "' to return from GET /applications/"
            + APP_LOAD_BALANCERS
            + "/loadBalancers");
  }

  @DisplayName(
      ".\n===\n"
          + "Given two kubernetes services\n"
          + "When sending get load balancers by account region and name request\n"
          + "Then only one service should be returned\n===")
  @Test
  public void shouldGetLoadBalancerByAccountRegionAndName()
      throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_LOAD_BALANCERS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "service",
        SERVICE_1_NAME,
        APP_LOAD_BALANCERS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, account1Ns, "service", "other", APP_LOAD_BALANCERS, kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/kubernetes/loadBalancers/"
                  + ACCOUNT1_NAME
                  + "/"
                  + account1Ns
                  + "/service "
                  + SERVICE_1_NAME;
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          List<Object> list =
              resp.jsonPath()
                  .getList(
                      "findAll { it.account == '"
                          + ACCOUNT1_NAME
                          + "' && it.region == '"
                          + account1Ns
                          + "' && it.name == 'service "
                          + SERVICE_1_NAME
                          + "' && it.moniker.app == '"
                          + APP_LOAD_BALANCERS
                          + "'}");
          return list != null && !list.isEmpty();
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'service "
            + SERVICE_1_NAME
            + "' to return from GET /kubernetes/loadBalancers/"
            + ACCOUNT1_NAME
            + "/"
            + account1Ns
            + "/service other");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes deployment\n"
          + "When sending get manifest by account, location and name request\n"
          + "Then only the desired manifest should be returned\n===")
  @Test
  public void shouldGetManifestByAccountLocationName() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_LOAD_BALANCERS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_LOAD_BALANCERS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/manifests/"
                  + ACCOUNT1_NAME
                  + "/"
                  + account1Ns
                  + "/deployment "
                  + DEPLOYMENT_1_NAME;
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("account").equals(ACCOUNT1_NAME)
              && resp.jsonPath().getString("location").equals(account1Ns)
              && resp.jsonPath().getString("name").equals("deployment " + DEPLOYMENT_1_NAME);
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for GET /manifests/"
            + ACCOUNT1_NAME
            + "/"
            + account1Ns
            + "/deployment "
            + DEPLOYMENT_1_NAME
            + " to return valid data");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes configmap\n"
          + "When sending get raw resources request\n"
          + "Then only the desired manifest should be returned\n===")
  // TODO: Uncomment after fixing rawResources endpoint
  //  @Test
  public void shouldGetRawResources() throws InterruptedException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns);
    String appName = "getrawresources";
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.name", "myconfig")
            .withValue("metadata.namespace", account1Ns)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, account1Ns, "configMap myconfig-v000");

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get rawResources request");
          Response resp = get(baseUrl() + "/applications/" + appName + "/rawResources");

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          List<Object> result = resp.jsonPath().getList("$");
          return result != null && !result.isEmpty();
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for GET /applications/"
            + appName
            + "/rawResources to return valid data");
  }

  @DisplayName(
      ".\n===\n"
          + "Given two kubernetes network policies\n"
          + "When sending get securityGroups\n"
          + "Then response should contain two lists with the security group for each\n===")
  @Test
  public void shouldListSecurityGroups() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println(
        "> Using namespace "
            + account1Ns
            + " and "
            + account2Ns
            + ", appName: "
            + APP_SECURITY_GROUPS);

    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "networkPolicy",
        NETWORK_POLICY_1_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT2_NAME,
        account2Ns,
        "networkPolicy",
        NETWORK_POLICY_1_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url = baseUrl() + "/securityGroups";
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());

          resp.then().statusCode(200);
          List<Object> list1 = resp.jsonPath().getList(ACCOUNT1_NAME + ".kubernetes." + account1Ns);
          List<Object> list2 = resp.jsonPath().getList(ACCOUNT2_NAME + ".kubernetes." + account2Ns);
          return list1 != null && !list1.isEmpty() && list2 != null && !list2.isEmpty();
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for GET /securityGroups"
            + " to return valid data");
  }

  @DisplayName(
      ".\n===\n"
          + "Given two kubernetes network policies for one account\n"
          + "When sending get securityGroups/{account}\n"
          + "Then response should contain a list with security groups of size 2\n===")
  @Test
  public void shouldListSecurityGroupsByAccount() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SECURITY_GROUPS);

    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "networkPolicy",
        NETWORK_POLICY_1_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "networkPolicy",
        NETWORK_POLICY_2_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url = baseUrl() + "/securityGroups/" + ACCOUNT1_NAME;
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          List<Object> securityGroupList = resp.jsonPath().getList("kubernetes." + account1Ns);
          return securityGroupList != null && securityGroupList.size() == 2;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for GET /securityGroups/"
            + ACCOUNT1_NAME
            + " to return valid data");
  }

  @DisplayName(
      ".\n===\n"
          + "Given two kubernetes network policies for different namespaces\n"
          + "When sending get securityGroups/{account}?region={region}\n"
          + "Then response should contain a list with security groups of size 1\n===")
  @Test
  public void shouldListSecurityGroupsByAccountAndRegion()
      throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SECURITY_GROUPS);

    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "networkPolicy",
        NETWORK_POLICY_1_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT2_NAME,
        account2Ns,
        "networkPolicy",
        NETWORK_POLICY_2_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url = baseUrl() + "/securityGroups/" + ACCOUNT1_NAME + "?region=" + account1Ns;
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          List<Object> securityGroupList = resp.jsonPath().getList("kubernetes." + account1Ns);
          return securityGroupList != null && securityGroupList.size() == 1;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for GET /securityGroups/"
            + ACCOUNT1_NAME
            + "?region="
            + account1Ns
            + " to return valid data");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes network policies for one account\n"
          + "When sending get securityGroups/{account}/{cloudprovider}\n"
          + "Then response should contain the securityGroup\n===")
  @Test
  public void shouldListSecurityGroupsByAccountAndCloudProvider()
      throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SECURITY_GROUPS);

    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "networkPolicy",
        NETWORK_POLICY_1_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url = baseUrl() + "/securityGroups/" + ACCOUNT1_NAME + "/kubernetes";
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          List<Object> securityGroupList = resp.jsonPath().getList(account1Ns);
          return securityGroupList != null && securityGroupList.size() > 0;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for GET /securityGroups/"
            + ACCOUNT1_NAME
            + "/kubernetes"
            + " to return valid data");
  }

  @DisplayName(
      ".\n===\n"
          + "Given two kubernetes network policies for one account\n"
          + "When sending get securityGroups/{account}/{cloudprovider}/{name}\n"
          + "Then response should contain the security group specified in name\n===")
  @Test
  public void shouldListSecurityGroupsByAccountAndCloudProviderAndName()
      throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SECURITY_GROUPS);

    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "networkPolicy",
        NETWORK_POLICY_1_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "networkPolicy",
        NETWORK_POLICY_2_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/securityGroups/"
                  + ACCOUNT1_NAME
                  + "/kubernetes/networkpolicy "
                  + NETWORK_POLICY_1_NAME;
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          List<Object> securityGroupList = resp.jsonPath().getList(account1Ns);
          return securityGroupList != null && securityGroupList.size() == 1;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for GET /securityGroups/"
            + ACCOUNT1_NAME
            + "/kubernetes/networkpolicy "
            + NETWORK_POLICY_1_NAME
            + " to return valid data");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes network policy for one account\n"
          + "When sending get securityGroups/{account}/{cloudProvider}/{region}/{securityGroupNameOrId}\n"
          + "Then response should contain the security group specified in securityGroupNameOrId\n===")
  @Test
  public void shouldGetSecurityGroupByAccountAndName() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SECURITY_GROUPS);

    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "networkPolicy",
        NETWORK_POLICY_1_NAME,
        APP_SECURITY_GROUPS,
        kubeCluster);
    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/securityGroups/"
                  + ACCOUNT1_NAME
                  + "/kubernetes/"
                  + account1Ns
                  + "/networkolicy "
                  + NETWORK_POLICY_1_NAME;
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          var displayName = resp.jsonPath().getString("displayName");
          return displayName != null && displayName.equals(NETWORK_POLICY_1_NAME);
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for GET /securityGroups/"
            + ACCOUNT1_NAME
            + "/kubernetes/"
            + account1Ns
            + "/networkolicy "
            + NETWORK_POLICY_1_NAME
            + " to return valid data");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes deployments\n"
          + "When sending get clusters /applications/{appName}/serverGroupManagers\n"
          + "Then the deployment should be present in serverGroups list of the response\n===")
  @Test
  public void shouldGetServerGroupManagerForApplication() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl() + "/applications/" + APP_SERVER_GROUP_MGRS + "/serverGroupManagers";
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          List<Object> serverGroupList =
              resp.jsonPath()
                  .getList(
                      "findAll { it.account == '"
                          + ACCOUNT1_NAME
                          + "' && it.namespace == '"
                          + account1Ns
                          + "' && it.name == 'deployment "
                          + DEPLOYMENT_1_NAME
                          + "'}");
          return serverGroupList != null && serverGroupList.size() == 1;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + "deployment"
            + "' cluster to return from GET /applications/"
            + APP_SERVER_GROUP_MGRS
            + "/serverGroupManagers");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes deployment of one application made by spinnaker\n"
          + "When sending get /applications/{application} request\n"
          + "Then an application object should be returned\n===")
  @Test
  public void shouldGetApplicationInCluster() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    System.out.println("> Using namespace " + account1Ns + ", appName: " + APP_SERVER_GROUP_MGRS);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        account1Ns,
        "deployment",
        DEPLOYMENT_1_NAME,
        APP_SERVER_GROUP_MGRS,
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url = baseUrl() + "/applications/" + APP_SERVER_GROUP_MGRS;
          System.out.println("> GET " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200).and();
          var appNameResp = resp.jsonPath().getString("name");
          return appNameResp != null && appNameResp.equals(APP_SERVER_GROUP_MGRS);
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + "deployment"
            + "' cluster to return from GET /applications/"
            + APP_SERVER_GROUP_MGRS);
  }
}
