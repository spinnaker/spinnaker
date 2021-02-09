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
import static org.junit.jupiter.api.Assertions.*;

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

  private static String ns;
  private static String deploymentName;
  private static String serviceName;

  @BeforeAll
  public static void setUpAll() {
    ns = kubeCluster.getAvailableNamespace();
    deploymentName = "clusternginx";
    serviceName = "lbnginx";
  }

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes deployment made by spinnaker to two accounts\n"
          + "When sending get clusters request\n"
          + "Then both deployments should be returned\n===")
  @Test
  public void shouldGetClusters() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, APP1_NAME, kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT2_NAME, "default", "deployment", deploymentName, APP1_NAME, kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get clusters request");
          Response resp = get(baseUrl() + "/applications/" + APP1_NAME + "/clusters");

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          resp.then().statusCode(200);
          List<Object> clustersAcc1 = resp.jsonPath().getList(ACCOUNT1_NAME);
          List<Object> clustersAcc2 = resp.jsonPath().getList(ACCOUNT1_NAME);
          return clustersAcc1 != null
              && clustersAcc1.contains("deployment " + deploymentName)
              && clustersAcc2 != null
              && clustersAcc2.contains("deployment " + deploymentName);
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for 'deployment "
            + deploymentName
            + "' cluster to return from GET /applications/"
            + APP1_NAME
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
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, APP1_NAME, kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT2_NAME, "default", "deployment", deploymentName, APP1_NAME, kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get clusters request");
          Response resp =
              get(baseUrl() + "/applications/" + APP1_NAME + "/clusters/" + ACCOUNT1_NAME);

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
                          + deploymentName
                          + "'}");
          Map<Object, Object> account2Map =
              resp.jsonPath().getMap("find { it.account == '" + ACCOUNT2_NAME + "'}");
          return (account1Map != null && !account1Map.isEmpty())
              && (account2Map == null || account2Map.isEmpty());
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for 'deployment "
            + deploymentName
            + "' cluster to return from GET /applications/"
            + APP1_NAME
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
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, APP1_NAME, kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", "other", APP1_NAME, kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get clusters request");
          Response resp =
              get(
                  baseUrl()
                      + "/applications/"
                      + APP1_NAME
                      + "/clusters/"
                      + ACCOUNT1_NAME
                      + "/deployment "
                      + deploymentName
                      + "?expand=true");

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
                          + deploymentName
                          + "' && it.application == '"
                          + APP1_NAME
                          + "'}");
          return map != null && !map.isEmpty() && resp.jsonPath().getList("$").size() == 1;
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for 'deployment "
            + deploymentName
            + "' cluster to return from GET /applications/"
            + APP1_NAME
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
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, APP1_NAME, kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", "other", APP1_NAME, kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get clusters request");
          Response resp =
              get(
                  baseUrl()
                      + "/applications/"
                      + APP1_NAME
                      + "/clusters/"
                      + ACCOUNT1_NAME
                      + "/deployment "
                      + deploymentName
                      + "/kubernetes?expand=true");

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("accountName").equals(ACCOUNT1_NAME)
              && resp.jsonPath().getString("name").equals("deployment " + deploymentName)
              && resp.jsonPath().getString("application").equals(APP1_NAME);
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for 'deployment "
            + deploymentName
            + "' cluster to return from GET /applications/"
            + APP1_NAME
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
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        APP1_NAME,
        "index.docker.io/library/nginx:1.14.0",
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        APP1_NAME,
        "index.docker.io/library/nginx:1.15.0",
        kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get server groups request");
          Response resp =
              get(baseUrl() + "/applications/" + APP1_NAME + "/serverGroups?expand=true");

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          resp.then().statusCode(200);
          List<Object> list =
              resp.jsonPath()
                  .getList(
                      "findAll { it.account == '"
                          + ACCOUNT1_NAME
                          + "' && it.region == '"
                          + ns
                          + "' && it.cluster == 'deployment "
                          + deploymentName
                          + "'}");
          return list != null && list.size() > 1;
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for at least two server groups to be returned from GET /applications/{app}/serverGroups");
  }

  @DisplayName(
      ".\n===\n"
          + "Given one deployment associated with two applications\n"
          + "When sending get server groups request by the two application\n"
          + "Then two server groups should be returned\n===")
  @Test
  public void shouldGetServerGroupsForApplications() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, APP1_NAME, kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", "other", APP2_NAME, kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get server groups request");
          Response resp =
              get(baseUrl() + "/serverGroups?applications=" + APP1_NAME + "," + APP2_NAME);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          resp.then().statusCode(200);
          List<Object> list =
              resp.jsonPath()
                  .getList(
                      "findAll { it.account == '"
                          + ACCOUNT1_NAME
                          + "' && it.region == '"
                          + ns
                          + "'}");
          return list != null && list.size() > 1;
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for at least two server groups to be returned from GET /serverGroups?applications");
  }

  @DisplayName(
      ".\n===\n"
          + "Given one deployment associated with two replicasets\n"
          + "When sending get server group request for account, region and name\n"
          + "Then only one server group should be returned\n===")
  @Test
  public void shouldGetServerGroupByMoniker() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        APP1_NAME,
        "index.docker.io/library/nginx:1.14.0",
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        APP1_NAME,
        "index.docker.io/library/nginx:1.15.0",
        kubeCluster);

    List<String> rsNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "get -n "
                        + ns
                        + " rs -o=jsonpath='{.items[?(@.metadata.ownerReferences[*].name==\""
                        + deploymentName
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
                  + ns
                  + "/replicaSet "
                  + rsNames.get(0);
          System.out.println("> Sending get server groups request to " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("name").equals("replicaSet " + rsNames.get(0));
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for 'replicaSet "
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
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        APP1_NAME,
        "index.docker.io/library/nginx:1.14.0",
        kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        APP1_NAME,
        "index.docker.io/library/nginx:1.15.0",
        kubeCluster);

    List<String> rsNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "get -n "
                        + ns
                        + " rs -o=jsonpath='{.items[?(@.metadata.ownerReferences[*].name==\""
                        + deploymentName
                        + "\")].metadata.name}'"));
    assertTrue(rsNames.size() > 1, "Expected more than one replicaset deployed");

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl()
                  + "/applications/"
                  + APP1_NAME
                  + "/serverGroups/"
                  + ACCOUNT1_NAME
                  + "/"
                  + ns
                  + "/replicaSet "
                  + rsNames.get(0);
          System.out.println("> Sending get server groups request to " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("name").equals("replicaSet " + rsNames.get(0));
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for 'replicaSet "
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
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", deploymentName)
            .withValue("metadata.namespace", ns)
            .withValue("spec.replicas", 2)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "deployment " + deploymentName);

    List<String> allPodNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "get -n " + ns + " pod -o=jsonpath='{.items[*].metadata.name}'"));
    List<String> podNames = new ArrayList<>();
    for (String name : allPodNames) {
      if (name.startsWith(deploymentName)) {
        podNames.add(name);
      }
    }
    assertFalse(podNames.isEmpty());

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          String url =
              baseUrl() + "/instances/" + ACCOUNT1_NAME + "/" + ns + "/pod " + podNames.get(0);
          System.out.println("> Sending get instances request to " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("displayName").equals(podNames.get(0));
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for 'pod "
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
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, APP1_NAME, kubeCluster);

    List<String> allPodNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "get -n " + ns + " pod -o=jsonpath='{.items[*].metadata.name}'"));
    List<String> podNames = new ArrayList<>();
    for (String name : allPodNames) {
      if (name.startsWith(deploymentName)) {
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
                  + ns
                  + "/pod "
                  + podNames.get(0)
                  + "/console?provider=kubernetes";
          System.out.println("> Sending get instance logs request to " + url);
          Response resp = get(url);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("output[0].output") != null;
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for logs of pod "
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
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "service", serviceName, APP1_NAME, kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT2_NAME, "default", "service", serviceName, APP1_NAME, kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get load balancers request");
          Response resp = get(baseUrl() + "/applications/" + APP1_NAME + "/loadBalancers");

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          resp.then().statusCode(200);
          Map<Object, Object> lbAcc1 =
              resp.jsonPath()
                  .getMap(
                      "find { it.account == '"
                          + ACCOUNT1_NAME
                          + "' && it.name == 'service "
                          + serviceName
                          + "' && it.namespace == '"
                          + ns
                          + "'}");
          Map<Object, Object> lbAcc2 =
              resp.jsonPath()
                  .getMap(
                      "find { it.account == '"
                          + ACCOUNT2_NAME
                          + "' && it.name == 'service "
                          + serviceName
                          + "' && it.namespace == 'default'}");
          return lbAcc1 != null && !lbAcc1.isEmpty() && lbAcc2 != null && !lbAcc2.isEmpty();
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for 'service "
            + serviceName
            + "' to return from GET /applications/"
            + APP1_NAME
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
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "service", serviceName, APP1_NAME, kubeCluster);
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "service", "other", APP1_NAME, kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get load balancers request");
          Response resp =
              get(
                  baseUrl()
                      + "/kubernetes/loadBalancers/"
                      + ACCOUNT1_NAME
                      + "/"
                      + ns
                      + "/service "
                      + serviceName);

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
                          + ns
                          + "' && it.name == 'service "
                          + serviceName
                          + "' && it.moniker.app == '"
                          + APP1_NAME
                          + "'}");
          return list != null && !list.isEmpty();
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for 'service "
            + serviceName
            + "' to return from GET /kubernetes/loadBalancers/"
            + ACCOUNT1_NAME
            + "/"
            + ns
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
    KubeTestUtils.deployIfMissing(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, APP1_NAME, kubeCluster);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get manifest request");
          Response resp =
              get(
                  baseUrl()
                      + "/manifests/"
                      + ACCOUNT1_NAME
                      + "/"
                      + ns
                      + "/deployment "
                      + deploymentName);

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          return resp.jsonPath().getString("account").equals(ACCOUNT1_NAME)
              && resp.jsonPath().getString("location").equals(ns)
              && resp.jsonPath().getString("name").equals("deployment " + deploymentName);
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for GET /manifests/"
            + ACCOUNT1_NAME
            + "/"
            + ns
            + "/deployment "
            + deploymentName
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
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.name", "myconfig")
            .withValue("metadata.namespace", ns)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "configMap myconfig-v000");

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get rawResources request");
          Response resp = get(baseUrl() + "/applications/" + APP1_NAME + "/rawResources");

          // ------------------------- then --------------------------
          System.out.println(resp.asString());
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().statusCode(200);
          List<Object> result = resp.jsonPath().getList("$");
          return result != null && !result.isEmpty();
        },
        1,
        TimeUnit.MINUTES,
        "Waited 1 minute for GET /applications/"
            + APP1_NAME
            + "/rawResources to return valid data");
  }
}
