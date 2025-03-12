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

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.KubeTestUtils;
import io.restassured.response.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DeployManifestIT extends BaseTest {

  private static final String DEPLOYMENT_1_NAME = "deployment1";
  private static final String REPLICASET_1_NAME = "rs1";
  private static final String SERVICE_1_NAME = "service1";

  private static final String SERVICE_2_NAME = "service2";
  private static String account1Ns;

  @BeforeAll
  public static void setUpAll() throws IOException, InterruptedException {
    account1Ns = kubeCluster.createNamespace(ACCOUNT1_NAME);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest with no namespace set\n"
          + "  And a namespace override\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then a pod is up and running in the overridden namespace\n===")
  @Test
  public void shouldDeployManifestFromText() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "deploy-from-text";
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .asList();
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", account1Ns)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
  }

  @DisplayName(
      ".\n===\n"
          + "Given mutiple manifests\n"
          + "  where only one satisfies the given label selector\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then only one manifest has been deployed\n===")
  @Test
  public void labelSelectors() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "deploy-from-text";
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmaps_with_selectors.yml")
            .withValue("metadata.namespace", account1Ns)
            .asList();
    Map<String, Object> labelSelectors =
        Map.of(
            "selectors",
            List.of(
                Map.of(
                    "kind", "EQUALS",
                    "key", "sample-configmap-selector",
                    "values", List.of("one"))));
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.labelSelectors", labelSelectors)
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "configMap sample-config-map-with-selector-one-v000");

    // ------------------------- then --------------------------
    String configMaps =
        kubeCluster.execKubectl("-n " + account1Ns + " get configmap -lselector-test=test -o name");
    assertThat(configMaps).hasLineCount(1);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest with default namespace set\n"
          + "  And a namespace override that is not listed in account's namespaces\n"
          + "When sending deploy manifest request\n"
          + "Then deployment fails with DescriptionValidationException\n===")
  @Test
  public void shouldNotDeployToNamespaceNotListed() {
    // ------------------------- given --------------------------
    String overrideNamespace = "nonexistent";
    String appName = "namespace-forbidden";
    System.out.println("> Using namespace: " + overrideNamespace + ", appName: " + appName);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("metadata.namespace", "default")
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", overrideNamespace)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    Response resp =
        given()
            .log()
            .uri()
            .contentType("application/json")
            .body(body)
            .post(baseUrl() + "/kubernetes/ops");

    // ------------------------- then --------------------------
    resp.then().statusCode(400);
    assertTrue(resp.body().asString().contains("wrongNamespace"));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest with no namespace set\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then a pod is up and running in the default namespace\n===")
  @Test
  public void shouldDeployManifestToDefaultNs() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "default-ns";
    System.out.println("> Using namespace: default, appName: " + appName);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, "default", "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n default get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n default"
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a document with multiple manifest definitions\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then a service and pod exist in the target cluster\n===")
  @Test
  public void shouldDeployMultidocManifest() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "deploy-multidoc";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/multi_deployment_service.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", appName)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + appName, "service " + appName);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + appName
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1", readyPods, "Expected one ready pod for " + appName + " deployment. Pods:\n" + pods);
    String services = kubeCluster.execKubectl("-n " + account1Ns + " get services");
    assertTrue(
        Strings.isNotEmpty(
            kubeCluster.execKubectl("-n " + account1Ns + " get services " + appName)),
        "Expected service " + appName + " to exist. Services: " + services);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment deployed with spinnaker\n"
          + "  And it gets updated with a new tag version\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then old version is deleted and new version is available\n===")
  @Test
  public void shouldUpdateExistingDeployment() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "update-deploy";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String oldImage = "index.docker.io/library/alpine:3.11";
    String newImage = "index.docker.io/library/alpine:3.12";

    List<Map<String, Object>> oldManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("spec.template.spec.containers[0].image", oldImage)
            .asList();

    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", oldManifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);
    String currentImage =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(
        oldImage, currentImage, "Expected correct " + DEPLOYMENT_1_NAME + " image to be deployed");

    List<Map<String, Object>> newManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("spec.template.spec.containers[0].image", newImage)
            .asList();

    // ------------------------- when --------------------------
    body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", newManifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
    currentImage =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(
        newImage, currentImage, "Expected correct " + DEPLOYMENT_1_NAME + " image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest without image tag\n"
          + "  And optional docker artifact present\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then the docker artifact is deployed\n===")
  @Test
  public void shouldBindOptionalDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "bind-optional";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String imageNoTag = "index.docker.io/library/alpine";
    String imageWithTag = "index.docker.io/library/alpine:3.12";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", imageWithTag)
            .withValue("version", imageWithTag.substring(imageNoTag.length() + 1))
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.optionalArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(
        imageWithTag,
        imageDeployed,
        "Expected correct " + DEPLOYMENT_1_NAME + " image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest without image tag\n"
          + "  And required docker artifact present\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then the docker artifact is deployed\n===")
  @Test
  public void shouldBindRequiredDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "bind-required";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String imageNoTag = "index.docker.io/library/alpine";
    String imageWithTag = "index.docker.io/library/alpine:3.12";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", imageWithTag)
            .withValue("version", imageWithTag.substring(imageNoTag.length() + 1))
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.requiredArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(
        imageWithTag,
        imageDeployed,
        "Expected correct " + DEPLOYMENT_1_NAME + " image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a replicaSet manifest without image tag\n"
          + "  And required docker artifact present\n"
          + "When sending deploy manifest request two times\n"
          + "Then there are two replicaSet versions deployed\n===")
  @Test
  public void shouldStepReplicaSetVersion() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "step-rs";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String imageNoTag = "index.docker.io/library/alpine";
    String imageWithTag = "index.docker.io/library/alpine:3.12";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", REPLICASET_1_NAME)
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .withValue(
                "spec.template.spec.containers[0].command",
                ImmutableList.of("tail", "-f", "/dev/null"))
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", imageWithTag)
            .withValue("version", imageWithTag.substring(imageNoTag.length() + 1))
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.requiredArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "replicaSet " + REPLICASET_1_NAME + "-v000");
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "replicaSet " + REPLICASET_1_NAME + "-v001");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get rs "
                + REPLICASET_1_NAME
                + "-v001 -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + REPLICASET_1_NAME + "-v001 replicaSet. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get rs "
                + REPLICASET_1_NAME
                + "-v001 -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(
        imageWithTag,
        imageDeployed,
        "Expected correct " + REPLICASET_1_NAME + "-v001 image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest without image tag\n"
          + "  And required docker artifact present\n"
          + "  And optional docker artifact present\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then required docker artifact is deployed\n===")
  @Test
  public void shouldBindRequiredOverOptionalDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "bind-required-over-optional";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String imageNoTag = "index.docker.io/library/alpine";
    String requiredImage = "index.docker.io/library/alpine:3.11";
    String optionalImage = "index.docker.io/library/alpine:3.12";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("spec.template.spec.containers[0].image", imageNoTag)
            .asList();
    Map<String, Object> requiredArtifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", requiredImage)
            .withValue("version", requiredImage.substring(imageNoTag.length() + 1))
            .asMap();
    Map<String, Object> optionalArtifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", optionalImage)
            .withValue("version", optionalImage.substring(imageNoTag.length() + 1))
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.requiredArtifacts[0]", requiredArtifact)
            .withValue("deployManifest.optionalArtifacts[0]", optionalArtifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(
        requiredImage,
        imageDeployed,
        "Expected correct " + DEPLOYMENT_1_NAME + " image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a manifest referencing an unversioned configmap\n"
          + "  And versioned configmap deployed\n"
          + "  And versioned configmap artifact\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then the manifest is deployed mounting versioned configmap\n===")
  @Test
  public void shouldBindVersionedConfigMap() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "bind-config-map";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String cmName = generateManifestName("myconfig");
    String version = "v005";

    // deploy versioned configmap
    Map<String, Object> cm =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", cmName + "-" + version)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", cm);

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_with_vol.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("spec.template.spec.volumes[0].configMap.name", cmName)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", cmName)
            .withValue("type", "kubernetes/configMap")
            .withValue("reference", cmName + "-" + version)
            .withValue("location", account1Ns)
            .withValue("version", version)
            .withValue("metadata.account", ACCOUNT1_NAME)
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.optionalArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
    String cmNameDeployed =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.spec.volumes[0].configMap.name}'");
    assertEquals(
        cmName + "-" + version, cmNameDeployed, "Expected correct configmap to be referenced");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a manifest referencing an unversioned secret\n"
          + "  And versioned secret deployed\n"
          + "  And versioned secret artifact\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then the manifest is deployed mounting versioned secret\n===")
  @Test
  public void shouldBindVersionedSecret() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "bind-secret";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String secretName = generateManifestName("mysecret");
    String version = "v009";

    // deploy versioned secret
    Map<String, Object> secret =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", secretName + "-" + version)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", secret);

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_with_vol.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("spec.template.spec.volumes[0].secret.secretName", secretName)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", secretName)
            .withValue("type", "kubernetes/secret")
            .withValue("reference", secretName + "-" + version)
            .withValue("location", account1Ns)
            .withValue("version", version)
            .withValue("metadata.account", ACCOUNT1_NAME)
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.optionalArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
    String secretNameDeployed =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.spec.volumes[0].secret.secretName}'");
    assertEquals(
        secretName + "-" + version, secretNameDeployed, "Expected correct secret to be referenced");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest with docker image tag\n"
          + "  And a required and optional docker artifacts\n"
          + "  And artifact binding disabled\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then the manifest is deployed with the original image tag in the manifest\n===")
  @Test
  public void shouldNotBindArtifacts() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "bind-disabled";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String imageInManifest = "index.docker.io/library/alpine:3.11";
    String requiredImage = "index.docker.io/library/alpine:3.12";
    String optionalImage = "index.docker.io/library/alpine:3.13";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("spec.template.spec.containers[0].image", imageInManifest)
            .asList();
    Map<String, Object> requiredArtifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageInManifest.substring(0, imageInManifest.indexOf(':')))
            .withValue("type", "docker/image")
            .withValue("reference", requiredImage)
            .withValue("version", requiredImage.substring(requiredImage.indexOf(':') + 1))
            .asMap();
    Map<String, Object> optionalArtifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageInManifest.substring(0, imageInManifest.indexOf(':')))
            .withValue("type", "docker/image")
            .withValue("reference", optionalImage)
            .withValue("version", optionalImage.substring(optionalImage.indexOf(':') + 1))
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.requiredArtifacts[0]", requiredArtifact)
            .withValue("deployManifest.optionalArtifacts[0]", optionalArtifact)
            .withValue("deployManifest.enableArtifactBinding", false)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + account1Ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.status.readyReplicas}'");
    assertEquals(
        "1",
        readyPods,
        "Expected one ready pod for " + DEPLOYMENT_1_NAME + " deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(
        imageInManifest,
        imageDeployed,
        "Expected correct " + DEPLOYMENT_1_NAME + " image to be deployed");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a configmap manifest\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then configmap is deployed with a version suffix name\n===")
  @Test
  public void shouldAddVersionToConfigmap() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "add-config-map-version";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String cmName = generateManifestName("myconfig");

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", cmName)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, account1Ns, "configMap " + cmName + "-v000");

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + account1Ns + " get cm " + cmName + "-v000");
    assertTrue(cm.contains("v000"), "Expected configmap with name " + cmName + "-v000");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a secret manifest\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then secret is deployed with a version suffix name\n===")
  @Test
  public void shouldAddVersionToSecret() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "add-secret-version";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String secretName = generateManifestName("mysecret");

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", secretName)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "secret " + secretName + "-v000");

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + account1Ns + " get secret " + secretName + "-v000");
    assertTrue(cm.contains("v000"), "Expected secret with name " + secretName + "-v000");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a configmap deployed with spinnaker\n"
          + "  And configmap manifest changed\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then a new version of configmap is deployed\n"
          + "  And the previous version of configmap is not deleted or changed\n===")
  @Test
  public void shouldDeployNewConfigmapVersion() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "new-config-map-version";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String cmName = generateManifestName("myconfig");

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", cmName)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, account1Ns, "configMap " + cmName + "-v000");

    manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", cmName)
            .withValue("data.newfile", "new content")
            .asList();

    // ------------------------- when --------------------------
    body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, account1Ns, "configMap " + cmName + "-v001");

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + account1Ns + " get cm " + cmName + "-v001");
    assertTrue(cm.contains("v001"), "Expected configmap with name " + cmName + "-v001");
    cm = kubeCluster.execKubectl("-n " + account1Ns + " get cm " + cmName + "-v000");
    assertTrue(cm.contains("v000"), "Expected configmap with name " + cmName + "-v000");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a secret deployed with spinnaker\n"
          + "  And secret manifest changed\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then a new version of secret is deployed\n"
          + "  And the previous version of secret is not deleted or changed\n===")
  @Test
  public void shouldDeployNewSecretVersion() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "new-secret-version";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String secretName = generateManifestName("mysecret");

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", secretName)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "secret " + secretName + "-v000");

    manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", secretName)
            .withValue("data.newfile", "SGVsbG8gd29ybGQK")
            .asList();

    // ------------------------- when --------------------------
    body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "secret " + secretName + "-v001");

    // ------------------------- then --------------------------
    String secret =
        kubeCluster.execKubectl("-n " + account1Ns + " get secret " + secretName + "-v001");
    assertTrue(secret.contains("v001"), "Expected secret with name " + secretName + "-v001");
    secret = kubeCluster.execKubectl("-n " + account1Ns + " get secret " + secretName + "-v000");
    assertTrue(secret.contains("v000"), "Expected secret with name " + secretName + "-v000");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a configmap manifest with special annotation to avoid being versioned\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then configmap is deployed without version\n===")
  @Test
  public void shouldNotAddVersionToConfigmap() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "unversioned-config-map";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String cmName = generateManifestName("myconfig");

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", cmName)
            .withValue(
                "metadata.annotations", ImmutableMap.of("strategy.spinnaker.io/versioned", "false"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, account1Ns, "configMap " + cmName);

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + account1Ns + " get cm " + cmName);
    assertFalse(cm.contains("v000"), "Expected configmap with name " + cmName);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a secret manifest with special annotation to avoid being versioned\n"
          + "When sending deploy manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then secret is deployed without version\n===")
  @Test
  public void shouldNotAddVersionToSecret() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "unversioned-secret";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String secretName = generateManifestName("mysecret");

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", secretName)
            .withValue(
                "metadata.annotations", ImmutableMap.of("strategy.spinnaker.io/versioned", "false"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, account1Ns, "secret " + secretName);

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + account1Ns + " get secret " + secretName);
    assertFalse(cm.contains("v000"), "Expected secret with name " + secretName);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a multidoc yaml with a service and replicaset\n"
          + "  And red/black deployment traffic strategy\n"
          + "When sending deploy manifest request two times\n"
          + "  And sending disable manifest one time\n"
          + "Then there are two replicasets with only the last one receiving traffic\n===")
  @Test
  public void shouldDeployRedBlackMultidoc() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "red-black-multidoc";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String selectorValue = appName + "traffichere";

    Map<String, Object> replicaset =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", appName)
            .withValue("spec.selector.matchLabels", ImmutableMap.of("label1", "value1"))
            .withValue("spec.template.metadata.labels", ImmutableMap.of("label1", "value1"))
            .asMap();
    Map<String, Object> service =
        KubeTestUtils.loadYaml("classpath:manifests/service.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", SERVICE_1_NAME)
            .withValue("spec.selector", ImmutableMap.of("pointer", selectorValue))
            .withValue("spec.type", "NodePort")
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", List.of(replicaset, service))
            .withValue(
                "deployManifest.services", Collections.singleton("service " + SERVICE_1_NAME))
            .withValue("deployManifest.strategy", "RED_BLACK")
            .withValue("deployManifest.trafficManagement.enabled", true)
            .withValue("deployManifest.trafficManagement.options.strategy", "redblack")
            .withValue("deployManifest.trafficManagement.options.enableTraffic", true)
            .withValue("deployManifest.trafficManagement.options.namespace", account1Ns)
            .withValue(
                "deployManifest.trafficManagement.options.services",
                Collections.singleton("service " + appName))
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        body,
        account1Ns,
        "service " + SERVICE_1_NAME,
        "replicaSet " + appName + "-v000");
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        body,
        account1Ns,
        "service " + SERVICE_1_NAME,
        "replicaSet " + appName + "-v001");
    body =
        KubeTestUtils.loadJson("classpath:requests/disable_manifest.json")
            .withValue("disableManifest.app", appName)
            .withValue("disableManifest.manifestName", "replicaSet " + appName + "-v000")
            .withValue("disableManifest.location", account1Ns)
            .withValue("disableManifest.account", ACCOUNT1_NAME)
            .asList();
    KubeTestUtils.disableManifest(baseUrl(), body, account1Ns, "replicaSet " + appName + "-v000");

    // ------------------------- then --------------------------
    List<String> podNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "-n "
                        + account1Ns
                        + " get pod -o=jsonpath='{.items[*].metadata.name}' -l=pointer="
                        + selectorValue));
    assertEquals(
        1, podNames.size(), "Only one pod expected to have the label for traffic selection");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a multidoc yaml with a service and replicaset\n"
          + "  And blue/green deployment traffic strategy\n"
          + "When sending deploy manifest request two times\n"
          + "  And sending disable manifest one time\n"
          + "Then there are two replicasets with only the last one receiving traffic\n===")
  @Test
  public void shouldDeployBlueGreenMultidoc() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "blue-green-multidoc";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String selectorValue = appName + "traffichere";

    Map<String, Object> replicaset =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", appName)
            .withValue("spec.selector.matchLabels", ImmutableMap.of("label1", "value1"))
            .withValue("spec.template.metadata.labels", ImmutableMap.of("label1", "value1"))
            .asMap();
    Map<String, Object> service =
        KubeTestUtils.loadYaml("classpath:manifests/service.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", SERVICE_2_NAME)
            .withValue("spec.selector", ImmutableMap.of("pointer", selectorValue))
            .withValue("spec.type", "NodePort")
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", List.of(replicaset, service))
            .withValue(
                "deployManifest.services", Collections.singleton("service " + SERVICE_2_NAME))
            .withValue("deployManifest.strategy", "BLUE_GREEN")
            .withValue("deployManifest.trafficManagement.enabled", true)
            .withValue("deployManifest.trafficManagement.options.strategy", "bluegreen")
            .withValue("deployManifest.trafficManagement.options.enableTraffic", true)
            .withValue("deployManifest.trafficManagement.options.namespace", account1Ns)
            .withValue(
                "deployManifest.trafficManagement.options.services",
                Collections.singleton("service " + appName))
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        body,
        account1Ns,
        "service " + SERVICE_2_NAME,
        "replicaSet " + appName + "-v000");
    KubeTestUtils.deployAndWaitStable(
        baseUrl(),
        body,
        account1Ns,
        "service " + SERVICE_2_NAME,
        "replicaSet " + appName + "-v001");
    body =
        KubeTestUtils.loadJson("classpath:requests/disable_manifest.json")
            .withValue("disableManifest.app", appName)
            .withValue("disableManifest.manifestName", "replicaSet " + appName + "-v000")
            .withValue("disableManifest.location", account1Ns)
            .withValue("disableManifest.account", ACCOUNT1_NAME)
            .asList();
    KubeTestUtils.disableManifest(baseUrl(), body, account1Ns, "replicaSet " + appName + "-v000");

    // ------------------------- then --------------------------
    List<String> podNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "-n "
                        + account1Ns
                        + " get pod -o=jsonpath='{.items[*].metadata.name}' -l=pointer="
                        + selectorValue));
    assertEquals(
        1, podNames.size(), "Only one pod expected to have the label for traffic selection");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a replicaset yaml with red/black deployment traffic strategy\n"
          + "  And an existing service\n"
          + "When sending deploy manifest request two times\n"
          + "  And sending disable manifest one time\n"
          + "Then there are two replicasets with only the last one receiving traffic\n===")
  @Test
  public void shouldDeployRedBlackReplicaSet() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "red-black";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String selectorValue = appName + "traffichere";

    Map<String, Object> service =
        KubeTestUtils.loadYaml("classpath:manifests/service.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", SERVICE_1_NAME)
            .withValue("spec.selector", ImmutableMap.of("pointer", selectorValue))
            .withValue("spec.type", "NodePort")
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", service);

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", appName)
            .withValue("spec.selector.matchLabels", ImmutableMap.of("label1", "value1"))
            .withValue("spec.template.metadata.labels", ImmutableMap.of("label1", "value1"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue(
                "deployManifest.services", Collections.singleton("service " + SERVICE_1_NAME))
            .withValue("deployManifest.strategy", "RED_BLACK")
            .withValue("deployManifest.trafficManagement.enabled", true)
            .withValue("deployManifest.trafficManagement.options.strategy", "redblack")
            .withValue("deployManifest.trafficManagement.options.enableTraffic", true)
            .withValue("deployManifest.trafficManagement.options.namespace", account1Ns)
            .withValue(
                "deployManifest.trafficManagement.options.services",
                Collections.singleton("service " + appName))
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "replicaSet " + appName + "-v000");
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "replicaSet " + appName + "-v001");
    body =
        KubeTestUtils.loadJson("classpath:requests/disable_manifest.json")
            .withValue("disableManifest.app", appName)
            .withValue("disableManifest.manifestName", "replicaSet " + appName + "-v000")
            .withValue("disableManifest.location", account1Ns)
            .withValue("disableManifest.account", ACCOUNT1_NAME)
            .asList();
    KubeTestUtils.disableManifest(baseUrl(), body, account1Ns, "replicaSet " + appName + "-v000");

    // ------------------------- then --------------------------
    List<String> podNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "-n "
                        + account1Ns
                        + " get pod -o=jsonpath='{.items[*].metadata.name}' -l=pointer="
                        + selectorValue));
    assertEquals(
        1, podNames.size(), "Only one pod expected to have the label for traffic selection");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a replicaset yaml with blue/green deployment traffic strategy\n"
          + "  And an existing service\n"
          + "When sending deploy manifest request two times\n"
          + "  And sending disable manifest one time\n"
          + "Then there are two replicasets with only the last one receiving traffic\n===")
  @Test
  public void shouldDeployBlueGreenReplicaSet() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "blue-green";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String selectorValue = appName + "traffichere";

    Map<String, Object> service =
        KubeTestUtils.loadYaml("classpath:manifests/service.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", SERVICE_2_NAME)
            .withValue("spec.selector", ImmutableMap.of("pointer", selectorValue))
            .withValue("spec.type", "NodePort")
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", service);

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", appName)
            .withValue("spec.selector.matchLabels", ImmutableMap.of("label1", "value1"))
            .withValue("spec.template.metadata.labels", ImmutableMap.of("label1", "value1"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue(
                "deployManifest.services", Collections.singleton("service " + SERVICE_2_NAME))
            .withValue("deployManifest.strategy", "BLUE_GREEN")
            .withValue("deployManifest.trafficManagement.enabled", true)
            .withValue("deployManifest.trafficManagement.options.strategy", "bluegreen")
            .withValue("deployManifest.trafficManagement.options.enableTraffic", true)
            .withValue("deployManifest.trafficManagement.options.namespace", account1Ns)
            .withValue(
                "deployManifest.trafficManagement.options.services",
                Collections.singleton("service " + appName))
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "replicaSet " + appName + "-v000");
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "replicaSet " + appName + "-v001");
    body =
        KubeTestUtils.loadJson("classpath:requests/disable_manifest.json")
            .withValue("disableManifest.app", appName)
            .withValue("disableManifest.manifestName", "replicaSet " + appName + "-v000")
            .withValue("disableManifest.location", account1Ns)
            .withValue("disableManifest.account", ACCOUNT1_NAME)
            .asList();
    KubeTestUtils.disableManifest(baseUrl(), body, account1Ns, "replicaSet " + appName + "-v000");

    // ------------------------- then --------------------------
    List<String> podNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "-n "
                        + account1Ns
                        + " get pod -o=jsonpath='{.items[*].metadata.name}' -l=pointer="
                        + selectorValue));
    assertEquals(
        1, podNames.size(), "Only one pod expected to have the label for traffic selection");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a cron job manifest without image tag\n"
          + "  And required docker artifact present\n"
          + "When sending cron job manifest request\n"
          + "  And waiting on manifest stable\n"
          + "Then the docker artifact is scheduled\n===")
  @Test
  public void shouldBindRequiredCronJobDockerImage() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "bind-required";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    String imageNoTag = "index.docker.io/library/alpine";
    String imageWithTag = "index.docker.io/library/alpine:3.12";
    String apiVersion = "batch/v1beta1";

    if (KubeTestUtils.compareVersion(KUBERNETES_VERSION, "v1.20") > 0) {
      apiVersion = "batch/v1";
    }

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/cronJob.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue("spec.jobTemplate.spec.template.spec.containers[0].image", imageNoTag)
            .withValue("apiVersion", apiVersion)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", imageNoTag)
            .withValue("type", "docker/image")
            .withValue("reference", imageWithTag)
            .withValue("version", imageWithTag.substring(imageNoTag.length() + 1))
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.requiredArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, account1Ns, "cronJob " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get cronjobs "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.spec.jobTemplate.spec.template.spec.containers[0].image}'");
    assertEquals(
        imageWithTag,
        imageDeployed,
        "Expected correct " + DEPLOYMENT_1_NAME + " image to be scheduled");
  }

  @DisplayName(
      ".\n===\n"
          + "Given k8s version < 1.22.0 and a v1beta1 CRD manifest\n"
          + "When sending deploy manifest request\n"
          + "Then a v1beta1 CRD is created\n===")
  @Test
  public void shouldDeployCrdV1beta1IfSupported() throws IOException, InterruptedException {
    if (KubeTestUtils.compareVersion(KUBERNETES_VERSION, "v1.21") > 0) {
      return;
    }
    // ------------------------- given --------------------------
    final String crdName = "crontabs.stable.example.com";
    final List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/crd_v1beta1.yml")
            .withValue("metadata.name", crdName)
            .asList();
    // ------------------------- when --------------------------
    final List<Map<String, Object>> request =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), request, "", String.format("customResourceDefinition %s", crdName));
    // ------------------------- then --------------------------
    String exits = kubeCluster.execKubectl(String.format("get crd %s", crdName));
    assertTrue(exits.contains(crdName));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a v1 CRD manifest\n"
          + "When sending deploy manifest request\n"
          + "Then a v1 CRD is created\n===")
  @Test
  public void shouldDeployCrdV1() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    final String crdName = "crontabs.stable.example.com";
    final List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/crd_v1.yml")
            .withValue("metadata.name", crdName)
            .asList();
    // ------------------------- when --------------------------
    final List<Map<String, Object>> request =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), request, "", String.format("customResourceDefinition %s", crdName));
    // ------------------------- then --------------------------
    String exits = kubeCluster.execKubectl(String.format("get crd %s", crdName));
    assertTrue(exits.contains(crdName));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a CRD namespaced scope version v1\n"
          + " And it's associated CR manifests"
          + "When sending deploy manifests request\n"
          + "Then a CRD and it's CR is created at namespaced level\n===")
  @Test
  public void shouldDeployCrCrdNamespacedV1() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    final String crdGroup = "stable.example.com";
    final String crdName = String.format("crontabs.%s", crdGroup);
    final String crName = "my-new-cron-object";
    final List<Map<String, Object>> crdManifest =
        KubeTestUtils.loadYaml("classpath:manifests/crd_v1.yml")
            .withValue("metadata.name", crdName)
            .withValue("spec.scope", "Namespaced")
            .asList();
    final List<Map<String, Object>> crManifest =
        KubeTestUtils.loadYaml("classpath:manifests/cr_v1.yml")
            .withValue("metadata.name", crName)
            .asList();
    // ------------------------- when --------------------------
    final List<Map<String, Object>> crdRequest =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", crdManifest)
            .asList();
    final List<Map<String, Object>> crRequest =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", crManifest)
            .withValue("deployManifest.namespaceOverride", account1Ns)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), crdRequest, "", String.format("customResourceDefinition %s", crdName));
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), crRequest, account1Ns, String.format("CronTab.%s %s", crdGroup, crName));
    // ------------------------- then --------------------------
    String crdExists = kubeCluster.execKubectl(String.format("get crd %s", crdName));
    String crNotExists =
        kubeCluster.execKubectl(String.format("get %s %s --ignore-not-found", crdName, crName));
    String crExists =
        kubeCluster.execKubectl(String.format("-n %s get %s %s", account1Ns, crdName, crName));
    assertTrue(
        crdExists.contains(crdName) && crExists.contains(crName) && !crNotExists.contains(crdName));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a CRD cluster scope version v1\n"
          + " And it's associated CR manifests"
          + "When sending deploy manifests request\n"
          + "Then a CRD and it's CR is created at cluster level\n===")
  @Test
  public void shouldDeployCrCrdClusterV1() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    final String crdGroup = "stable.cluster.com";
    final String crdName = String.format("crontabs.%s", crdGroup);
    final String crName = "my-new-cron-object";
    kubeCluster.execKubectl(String.format("delete crd %s --ignore-not-found", crdName));
    final List<Map<String, Object>> crdManifest =
        KubeTestUtils.loadYaml("classpath:manifests/crd_v1.yml")
            .withValue("metadata.name", crdName)
            .withValue("spec.scope", "Cluster")
            .withValue("spec.group", crdGroup)
            .asList();
    final List<Map<String, Object>> crManifest =
        KubeTestUtils.loadYaml("classpath:manifests/cr_v1.yml")
            .withValue("apiVersion", String.format("%s/v1", crdGroup))
            .withValue("metadata.name", crName)
            .asList();
    // ------------------------- when --------------------------
    final List<Map<String, Object>> crdRequest =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", crdManifest)
            .asList();
    final List<Map<String, Object>> crRequest =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", crManifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), crdRequest, "", String.format("customResourceDefinition %s", crdName));
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), crRequest, "", String.format("CronTab.%s %s", crdGroup, crName));
    // ------------------------- then --------------------------
    String crdExists = kubeCluster.execKubectl(String.format("get crd %s", crdName));
    String crExists = kubeCluster.execKubectl(String.format("get %s %s", crdName, crName));
    String crExistsNamespaced =
        kubeCluster.execKubectl(String.format("-n %s get %s %s", account1Ns, crdName, crName));
    assertTrue(
        crdExists.contains(crdName)
            && crExists.contains(crName)
            && crExistsNamespaced.contains(crName));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a CRD already hardcoded in the account configuration\n"
          + "When sending credentials request\n"
          + "Then the credentials response contains the deployed CRD\n===")
  @Test
  public void shouldGetDeployedCrdsCredentials() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    // ------------------------- when --------------------------
    Response response =
        given()
            .log()
            .body(false)
            .queryParam("expand", true)
            .get(String.format("%s/credentials", baseUrl()));
    // ------------------------- then --------------------------
    System.out.println(response.prettyPrint());
    response
        .then()
        .statusCode(200)
        .body(
            "spinnakerKindMap.'crontab.stable.example.com'.findAll{ e -> e != null }",
            hasSize(greaterThan(0)));
  }

  private static String generateManifestName(String myconfig) {
    return myconfig + Long.toHexString(UUID.randomUUID().getLeastSignificantBits());
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployed manifest with an special annotation to avoid being versioned\n"
          + "  And another annotation to avoid updating the replicas amount"
          + "When sending an updated manifest\n"
          + "  With a a new env var\n"
          + "  And a different replica size\n"
          + "The manifest is deployed with the new env var and the old replicas size\n===")
  @Test
  public void shouldUseSourceCapacityNonVersioned() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    int originalReplicasSize = 1;
    int secondReplicasSize = 5;
    ImmutableMap<String, String> annotations =
        ImmutableMap.of(
            "strategy.spinnaker.io/versioned",
            "false" // non-versioned
            ,
            "strategy.spinnaker.io/use-source-capacity",
            "true" // do not update replicas
            );
    String appName = "unversionedsourcepacaity-deployment";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", appName)
            .withValue("spec.selector.matchLabels.app", appName)
            .withValue("spec.template.metadata.labels.app", appName)
            .withValue("spec.replicas", originalReplicasSize)
            .withValue("metadata.annotations", annotations)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, account1Ns, "deployment " + appName);

    // ------------------------- when --------------------------
    List<Map<String, Object>> secondManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", appName)
            .withValue("spec.selector.matchLabels.app", appName)
            .withValue("spec.template.metadata.labels.app", appName)
            .withValue("spec.replicas", secondReplicasSize)
            .withValue(
                "spec.template.spec.containers[0].env",
                Collections.singletonList(
                    ImmutableMap.of(
                        "name", "test",
                        "value", "test")))
            .withValue("metadata.annotations", annotations)
            .asList();
    List<Map<String, Object>> secondBody =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", secondManifest)
            .asList();
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    KubeTestUtils.deployAndWaitStable(baseUrl(), secondBody, account1Ns, "deployment " + appName);

    // ------------------------- then --------------------------
    String currentReplicas =
        kubeCluster.execKubectl(
            "-n " + account1Ns + " get deployment " + appName + " -o=jsonpath='{.spec.replicas}'");
    assertEquals(
        String.valueOf(originalReplicasSize),
        currentReplicas,
        "Expected "
            + originalReplicasSize
            + " replica for "
            + appName
            + " deployment. Pods:\n"
            + currentReplicas);

    String envVarValue =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + appName
                + " -o=jsonpath='{.spec.template.spec.containers[0].env[0].value}'");
    assertEquals("test", envVarValue, "Expected update env var for " + appName + " deployment.\n");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a replicaset manifest with an special annotation for versioning\n"
          + "  And another annotation to avoid updating the replicas amount"
          + "When sending an updated manifest\n"
          + "  With a a new env var\n"
          + "  And a different replica size\n"
          + "The manifest is deployed with the new env var and the replicas value from the previous version\n===")
  @Test
  public void shouldUseSourceCapacityVersioned() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    int originalReplicasSize = 1;
    int secondReplicasSize = 5;
    ImmutableMap<String, String> annotations =
        ImmutableMap.of(
            "strategy.spinnaker.io/versioned",
            "true" // versioned
            ,
            "strategy.spinnaker.io/use-source-capacity",
            "true" // do not update replicas
            );
    String appName = "unversionedsourcepacaity-replicaset";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", appName)
            .withValue("spec.selector.matchLabels.app", appName)
            .withValue("spec.template.metadata.labels.app", appName)
            .withValue("spec.replicas", originalReplicasSize)
            .withValue("metadata.annotations", annotations)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "replicaSet " + appName + "-v000");

    // ------------------------- when --------------------------
    List<Map<String, Object>> secondManifest =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", appName)
            .withValue("spec.selector.matchLabels.app", appName)
            .withValue("spec.template.metadata.labels.app", appName)
            .withValue("spec.replicas", secondReplicasSize)
            .withValue(
                "spec.template.spec.containers[0].env",
                Collections.singletonList(
                    ImmutableMap.of(
                        "name", "test",
                        "value", "test")))
            .withValue("metadata.annotations", annotations)
            .asList();
    List<Map<String, Object>> secondBody =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", secondManifest)
            .asList();
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), secondBody, account1Ns, "replicaSet " + appName + "-v001");

    // ------------------------- then --------------------------
    String currentReplicas =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get replicaSet "
                + appName
                + "-v001"
                + " -o=jsonpath='{.spec.replicas}'");
    assertEquals(
        String.valueOf(originalReplicasSize),
        currentReplicas,
        "Expected "
            + originalReplicasSize
            + " replica for "
            + appName
            + " replicaset. Pods:\n"
            + currentReplicas);

    String envVarValue =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get replicaSet "
                + appName
                + "-v001"
                + " -o=jsonpath='{.spec.template.spec.containers[0].env[0].value}'");
    assertEquals("test", envVarValue, "Expected update env var for " + appName + " replicaset.\n");
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest with server-side-apply strategy set\n"
          + "When sending deploy manifest request\n"
          + "Then a deployment is created using server-side apply\n===")
  @Test
  public void shouldDeployUsingServerSideApply() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "server-side-apply";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue(
                "metadata.annotations",
                ImmutableMap.of("strategy.spinnaker.io/server-side-apply", "force-conflicts"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    /* Expecting:
      metadata:
        managedFields:
        - manager: kubectl
          operation: Apply
          fieldsType: FieldsV1
    */
    String managedFields =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.metadata.managedFields}'");
    assertTrue(
        Strings.isNotEmpty(managedFields),
        "Expected managedFields for "
            + DEPLOYMENT_1_NAME
            + " deployment to exist and be managed server-side. managedFields:\n"
            + managedFields);

    String applyManager =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.metadata.managedFields[?(@.operation==\"Apply\")].manager}'");
    // kubectl v1.26+ adds a "kubectl-last-applied" manager as well. Remove it. The jsonpath
    // implementation in kubectl is really limited, so we have to do this in java.
    applyManager = applyManager.replaceAll("\\s?kubectl-last-applied\\s?", "");
    assertEquals(
        "kubectl",
        applyManager,
        "Expected apply manager for "
            + DEPLOYMENT_1_NAME
            + " deployment to be managed server-side. managedFields:\n"
            + managedFields);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest with server-side-apply disabled set\n"
          + "When sending deploy manifest request\n"
          + "Then a deployment is created using client-side apply\n===")
  @Test
  public void shouldDeployUsingApplyWithServerSideApplyDisabled()
      throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "server-side-apply-disabled";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .withValue(
                "metadata.annotations",
                ImmutableMap.of("strategy.spinnaker.io/server-side-apply", "false"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String lastAppliedConfiguration =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.metadata.annotations.kubectl\\.kubernetes\\.io/last-applied-configuration}'");
    assertTrue(
        Strings.isNotEmpty(lastAppliedConfiguration),
        "Expected last-applied-configuration for "
            + DEPLOYMENT_1_NAME
            + " deployment to exist and be managed client-side. fields:\n"
            + lastAppliedConfiguration);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest without a strategy set\n"
          + "When sending deploy manifest request\n"
          + "Then a deployment is created using client-side apply\n===")
  @Test
  public void shouldDeployUsingClientApply() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String appName = "client-side-apply";
    System.out.println("> Using namespace: " + account1Ns + ", appName: " + appName);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", DEPLOYMENT_1_NAME)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", appName)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, account1Ns, "deployment " + DEPLOYMENT_1_NAME);

    // ------------------------- then --------------------------
    String lastAppliedConfiguration =
        kubeCluster.execKubectl(
            "-n "
                + account1Ns
                + " get deployment "
                + DEPLOYMENT_1_NAME
                + " -o=jsonpath='{.metadata.annotations.kubectl\\.kubernetes\\.io/last-applied-configuration}'");
    assertTrue(
        Strings.isNotEmpty(lastAppliedConfiguration),
        "Expected last-applied-configuration for "
            + DEPLOYMENT_1_NAME
            + " deployment to exist and be managed client-side. fields:\n"
            + lastAppliedConfiguration);
  }
}
