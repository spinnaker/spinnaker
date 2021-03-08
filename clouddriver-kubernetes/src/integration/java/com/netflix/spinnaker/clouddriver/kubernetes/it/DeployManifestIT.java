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
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.KubeTestUtils;
import io.restassured.response.Response;
import java.io.IOException;
import java.util.*;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;

public class DeployManifestIT extends BaseTest {

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
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml").asList();
    String overrideNamespace = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + overrideNamespace);

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", overrideNamespace)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, overrideNamespace, "deployment myapp");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + overrideNamespace + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n "
                + overrideNamespace
                + " get deployment myapp -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for myapp deployment. Pods:\n" + pods);
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
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", "default")
            .asList();
    String overrideNamespace = "nonexistent";
    System.out.println("> Using namespace " + overrideNamespace);

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", overrideNamespace)
            .withValue("deployManifest.moniker.app", APP1_NAME)
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
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml").asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT2_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, "default", "deployment myapp");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n default get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n default" + " get deployment myapp -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for myapp deployment. Pods:\n" + pods);
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/multi_deployment_service.yml")
            .withValue("metadata.namespace", ns)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "deployment myapp", "service myservice");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment myapp -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for myapp deployment. Pods:\n" + pods);
    String services = kubeCluster.execKubectl("-n " + ns + " get services");
    assertTrue(
        Strings.isNotEmpty(kubeCluster.execKubectl("-n " + ns + " get services myservice")),
        "Expected service myservice to exist. Services: " + services);
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String oldImage = "index.docker.io/library/alpine:3.11";
    String newImage = "index.docker.io/library/alpine:3.12";

    List<Map<String, Object>> oldManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.containers[0].image", oldImage)
            .asList();

    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", oldManifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "deployment myapp");
    String currentImage =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment myapp -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(oldImage, currentImage, "Expected correct myapp image to be deployed");

    List<Map<String, Object>> newManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.containers[0].image", newImage)
            .asList();

    // ------------------------- when --------------------------
    body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", newManifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "deployment myapp");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment myapp -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for myapp deployment. Pods:\n" + pods);
    currentImage =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment myapp -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(newImage, currentImage, "Expected correct myapp image to be deployed");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String imageNoTag = "index.docker.io/library/alpine";
    String imageWithTag = "index.docker.io/library/alpine:3.12";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", ns)
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
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.optionalArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "deployment myapp");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment myapp -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for myapp deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment myapp -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(imageWithTag, imageDeployed, "Expected correct myapp image to be deployed");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String imageNoTag = "index.docker.io/library/alpine";
    String imageWithTag = "index.docker.io/library/alpine:3.12";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", ns)
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
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.requiredArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "deployment myapp");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment myapp -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for myapp deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment myapp -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(imageWithTag, imageDeployed, "Expected correct myapp image to be deployed");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String imageNoTag = "index.docker.io/library/alpine";
    String requiredImage = "index.docker.io/library/alpine:3.11";
    String optionalImage = "index.docker.io/library/alpine:3.12";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.namespace", ns)
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
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.requiredArtifacts[0]", requiredArtifact)
            .withValue("deployManifest.optionalArtifacts[0]", optionalArtifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "deployment myapp");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment myapp -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for myapp deployment. Pods:\n" + pods);
    String imageDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment myapp -o=jsonpath='{.spec.template.spec.containers[0].image}'");
    assertEquals(requiredImage, imageDeployed, "Expected correct myapp image to be deployed");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String cmName = "myconfig";
    String version = "v005";

    // deploy versioned configmap
    Map<String, Object> cm =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName + "-" + version)
            .asMap();
    kubeCluster.execKubectl("-n " + ns + " apply -f -", cm);

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_with_vol.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.volumes[0].configMap.name", cmName)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", cmName)
            .withValue("type", "kubernetes/configMap")
            .withValue("reference", cmName + "-" + version)
            .withValue("location", ns)
            .withValue("version", version)
            .withValue("metadata.account", ACCOUNT1_NAME)
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.optionalArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "deployment myapp");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment myapp -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for myapp deployment. Pods:\n" + pods);
    String cmNameDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment myapp -o=jsonpath='{.spec.template.spec.volumes[0].configMap.name}'");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String secretName = "mysecret";
    String version = "v009";

    // deploy versioned secret
    Map<String, Object> secret =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName + "-" + version)
            .asMap();
    kubeCluster.execKubectl("-n " + ns + " apply -f -", secret);

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment_with_vol.yml")
            .withValue("metadata.namespace", ns)
            .withValue("spec.template.spec.volumes[0].secret.secretName", secretName)
            .asList();
    Map<String, Object> artifact =
        KubeTestUtils.loadJson("classpath:requests/artifact.json")
            .withValue("name", secretName)
            .withValue("type", "kubernetes/secret")
            .withValue("reference", secretName + "-" + version)
            .withValue("location", ns)
            .withValue("version", version)
            .withValue("metadata.account", ACCOUNT1_NAME)
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.optionalArtifacts[0]", artifact)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "deployment myapp");

    // ------------------------- then --------------------------
    String pods = kubeCluster.execKubectl("-n " + ns + " get pods");
    String readyPods =
        kubeCluster.execKubectl(
            "-n " + ns + " get deployment myapp -o=jsonpath='{.status.readyReplicas}'");
    assertEquals("1", readyPods, "Expected one ready pod for myapp deployment. Pods:\n" + pods);
    String secretNameDeployed =
        kubeCluster.execKubectl(
            "-n "
                + ns
                + " get deployment myapp -o=jsonpath='{.spec.template.spec.volumes[0].secret.secretName}'");
    assertEquals(
        secretName + "-" + version, secretNameDeployed, "Expected correct secret to be referenced");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String cmName = "myconfig";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "configMap " + cmName + "-v000");

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get cm " + cmName + "-v000");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String secretName = "mysecret";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName)
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "secret " + secretName + "-v000");

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get secret " + secretName + "-v000");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String cmName = "myconfig";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "configMap " + cmName + "-v000");

    manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName)
            .withValue("data.newfile", "new content")
            .asList();

    // ------------------------- when --------------------------
    body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "configMap " + cmName + "-v001");

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get cm " + cmName + "-v001");
    assertTrue(cm.contains("v001"), "Expected configmap with name " + cmName + "-v001");
    cm = kubeCluster.execKubectl("-n " + ns + " get cm " + cmName + "-v000");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String secretName = "mysecret";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName)
            .asList();
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "secret " + secretName + "-v000");

    manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName)
            .withValue("data.newfile", "SGVsbG8gd29ybGQK")
            .asList();

    // ------------------------- when --------------------------
    body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "secret " + secretName + "-v001");

    // ------------------------- then --------------------------
    String secret = kubeCluster.execKubectl("-n " + ns + " get secret " + secretName + "-v001");
    assertTrue(secret.contains("v001"), "Expected secret with name " + secretName + "-v001");
    secret = kubeCluster.execKubectl("-n " + ns + " get secret " + secretName + "-v000");
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String cmName = "myconfig";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/configmap.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", cmName)
            .withValue(
                "metadata.annotations", ImmutableMap.of("strategy.spinnaker.io/versioned", "false"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "configMap " + cmName);

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get cm " + cmName);
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String secretName = "mysecret";

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", secretName)
            .withValue(
                "metadata.annotations", ImmutableMap.of("strategy.spinnaker.io/versioned", "false"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "secret " + secretName);

    // ------------------------- then --------------------------
    String cm = kubeCluster.execKubectl("-n " + ns + " get secret " + secretName);
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String name = "myapp";
    String selectorValue = "traffichere";

    Map<String, Object> replicaset =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", name)
            .withValue("spec.selector.matchLabels", ImmutableMap.of("label1", "value1"))
            .withValue("spec.template.metadata.labels", ImmutableMap.of("label1", "value1"))
            .asMap();
    Map<String, Object> service =
        KubeTestUtils.loadYaml("classpath:manifests/service.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", name)
            .withValue("spec.selector", ImmutableMap.of("pointer", selectorValue))
            .withValue("spec.type", "NodePort")
            .asMap();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", List.of(replicaset, service))
            .withValue("deployManifest.services", Collections.singleton("service " + name))
            .withValue("deployManifest.strategy", "RED_BLACK")
            .withValue("deployManifest.trafficManagement.enabled", true)
            .withValue("deployManifest.trafficManagement.options.strategy", "redblack")
            .withValue("deployManifest.trafficManagement.options.enableTraffic", true)
            .withValue("deployManifest.trafficManagement.options.namespace", ns)
            .withValue(
                "deployManifest.trafficManagement.options.services",
                Collections.singleton("service " + name))
            .asList();
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, ns, "service " + name, "replicaSet " + name + "-v000");
    KubeTestUtils.deployAndWaitStable(
        baseUrl(), body, ns, "service " + name, "replicaSet " + name + "-v001");
    body =
        KubeTestUtils.loadJson("classpath:requests/disable_manifest.json")
            .withValue("disableManifest.app", APP1_NAME)
            .withValue("disableManifest.manifestName", "replicaSet " + name + "-v000")
            .withValue("disableManifest.location", ns)
            .withValue("disableManifest.account", ACCOUNT1_NAME)
            .asList();
    KubeTestUtils.disableManifest(baseUrl(), body, ns);

    // ------------------------- then --------------------------
    String port =
        kubeCluster.execKubectl(
            "-n " + ns + " get service " + name + " -o=jsonpath='{.spec.ports[0].nodePort}'");
    Container.ExecResult result =
        kubeCluster.execInContainer("wget", "http://localhost:" + port, "-O", "-");
    assertEquals(
        0,
        result.getExitCode(),
        "stdout: " + result.getStdout() + " stderr: " + result.getStderr());
    List<String> podNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "-n "
                        + ns
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
    String ns = kubeCluster.getAvailableNamespace();
    System.out.println("> Using namespace " + ns);
    String name = "myapp";
    String selectorValue = "traffichere";

    Map<String, Object> service =
        KubeTestUtils.loadYaml("classpath:manifests/service.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", name)
            .withValue("spec.selector", ImmutableMap.of("pointer", selectorValue))
            .withValue("spec.type", "NodePort")
            .asMap();
    kubeCluster.execKubectl("-n " + ns + " apply -f -", service);

    List<Map<String, Object>> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.namespace", ns)
            .withValue("metadata.name", name)
            .withValue("spec.selector.matchLabels", ImmutableMap.of("label1", "value1"))
            .withValue("spec.template.metadata.labels", ImmutableMap.of("label1", "value1"))
            .asList();

    // ------------------------- when --------------------------
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", manifest)
            .withValue("deployManifest.services", Collections.singleton("service " + name))
            .withValue("deployManifest.strategy", "RED_BLACK")
            .withValue("deployManifest.trafficManagement.enabled", true)
            .withValue("deployManifest.trafficManagement.options.strategy", "redblack")
            .withValue("deployManifest.trafficManagement.options.enableTraffic", true)
            .withValue("deployManifest.trafficManagement.options.namespace", ns)
            .withValue(
                "deployManifest.trafficManagement.options.services",
                Collections.singleton("service " + name))
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "replicaSet " + name + "-v000");
    KubeTestUtils.deployAndWaitStable(baseUrl(), body, ns, "replicaSet " + name + "-v001");
    body =
        KubeTestUtils.loadJson("classpath:requests/disable_manifest.json")
            .withValue("disableManifest.app", APP1_NAME)
            .withValue("disableManifest.manifestName", "replicaSet " + name + "-v000")
            .withValue("disableManifest.location", ns)
            .withValue("disableManifest.account", ACCOUNT1_NAME)
            .asList();
    KubeTestUtils.disableManifest(baseUrl(), body, ns);

    // ------------------------- then --------------------------
    String port =
        kubeCluster.execKubectl(
            "-n " + ns + " get service " + name + " -o=jsonpath='{.spec.ports[0].nodePort}'");
    Container.ExecResult result =
        kubeCluster.execInContainer("wget", "http://localhost:" + port, "-O", " -");
    assertEquals(
        0,
        result.getExitCode(),
        "stdout: " + result.getStdout() + " stderr: " + result.getStderr());
    List<String> podNames =
        Splitter.on(" ")
            .splitToList(
                kubeCluster.execKubectl(
                    "-n "
                        + ns
                        + " get pod -o=jsonpath='{.items[*].metadata.name}' -l=pointer="
                        + selectorValue));
    assertEquals(
        1, podNames.size(), "Only one pod expected to have the label for traffic selection");
  }
}
