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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class InfrastructureIT extends BaseTest {

  private static final int CACHE_TIMEOUT_MIN = 5;

  @DisplayName(
      ".\n===\n"
          + "Given a kubernetes deployment made by spinnaker to two accounts\n"
          + "When sending get clusters request\n"
          + "Then both deployments should be returned\n===")
  @Test
  public void shouldGetClusters() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getclusters";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, appName);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT2_NAME, "default", "deployment", deploymentName, appName);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get clusters request");
          Response resp = get(baseUrl() + "/applications/" + appName + "/clusters");

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
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + deploymentName
            + "' cluster to return from GET /applications/"
            + appName
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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getclustersbyaccount";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, appName);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT2_NAME, "default", "deployment", deploymentName, appName);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get clusters request");
          Response resp =
              get(baseUrl() + "/applications/" + appName + "/clusters/" + ACCOUNT1_NAME);

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
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + deploymentName
            + "' cluster to return from GET /applications/"
            + appName
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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getclustersbyaccountandname";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, appName);
    KubeTestUtils.deployAndWaitStable(baseUrl(), ACCOUNT1_NAME, ns, "deployment", "other", appName);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get clusters request");
          Response resp =
              get(
                  baseUrl()
                      + "/applications/"
                      + appName
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
                          + appName
                          + "'}");
          return map != null && !map.isEmpty() && resp.jsonPath().getList("$").size() == 1;
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + deploymentName
            + "' cluster to return from GET /applications/"
            + appName
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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getclustersbyaccountnametype";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, appName);
    KubeTestUtils.deployAndWaitStable(baseUrl(), ACCOUNT1_NAME, ns, "deployment", "other", appName);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get clusters request");
          Response resp =
              get(
                  baseUrl()
                      + "/applications/"
                      + appName
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
              && resp.jsonPath().getString("application").equals(appName);
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'deployment "
            + deploymentName
            + "' cluster to return from GET /applications/"
            + appName
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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getservergroups";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        appName,
        "index.docker.io/library/alpine:3.11");
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        appName,
        "index.docker.io/library/alpine:3.12");

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get server groups request");
          Response resp = get(baseUrl() + "/applications/" + appName + "/serverGroups?expand=true");

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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getservergroupsapp";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, appName);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", "other", APP2_NAME);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get server groups request");
          Response resp =
              get(baseUrl() + "/serverGroups?applications=" + appName + "," + APP2_NAME);

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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getservergroupsmoniker";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        appName,
        "index.docker.io/library/alpine:3.11");
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        appName,
        "index.docker.io/library/alpine:3.12");

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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getservergroupbyapp";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        appName,
        "index.docker.io/library/alpine:3.11");
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        ACCOUNT1_NAME,
        ns,
        "deployment",
        deploymentName,
        appName,
        "index.docker.io/library/alpine:3.12");

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
                  + appName
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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getinstancebyaccount";
    System.out.println("> Using namespace " + ns);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", deploymentName)
            .withValue("metadata.namespace", ns)
            .withValue("spec.replicas", 2)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getinstancelogs";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, appName);

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
    String ns = kubeCluster.getAvailableNamespace();
    String serviceName = "inframyservice";
    String appName = "getloadbalancers";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "service", serviceName, appName);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT2_NAME, "default", "service", serviceName, appName);

    KubeTestUtils.repeatUntilTrue(
        () -> {
          // ------------------------- when --------------------------
          System.out.println("> Sending get load balancers request");
          Response resp = get(baseUrl() + "/applications/" + appName + "/loadBalancers");

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
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'service "
            + serviceName
            + "' to return from GET /applications/"
            + appName
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
    String ns = kubeCluster.getAvailableNamespace();
    String serviceName = "inframyservice";
    String appName = "getloadbalancersbyparams";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "service", serviceName, appName);
    KubeTestUtils.deployAndWaitStable(baseUrl(), ACCOUNT1_NAME, ns, "service", "other", appName);

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
                          + appName
                          + "'}");
          return list != null && !list.isEmpty();
        },
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for 'service "
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
    String ns = kubeCluster.getAvailableNamespace();
    String deploymentName = "inframyapp";
    String appName = "getmanifest";
    System.out.println("> Using namespace " + ns);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), ACCOUNT1_NAME, ns, "deployment", deploymentName, appName);

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
        CACHE_TIMEOUT_MIN,
        TimeUnit.MINUTES,
        "Waited "
            + CACHE_TIMEOUT_MIN
            + " minutes for GET /manifests/"
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
  public void shouldGetRawResources() throws InterruptedException, IOException {
    // ------------------------- given --------------------------
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String appName = "getrawresources";
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.name", "myconfig")
            .withValue("metadata.namespace", ns)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "configMap myconfig-v000");

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
}
