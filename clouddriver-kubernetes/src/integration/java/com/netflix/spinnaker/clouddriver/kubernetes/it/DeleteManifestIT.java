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
import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.clouddriver.kubernetes.it.utils.KubeTestUtils;
import io.restassured.response.Response;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

public class DeleteManifestIT extends BaseTest {

  private static String account1Ns;

  @BeforeAll
  public static void setUpAll() throws IOException, InterruptedException {
    account1Ns = kubeCluster.createNamespace(ACCOUNT1_NAME);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a secret deployed outside of Spinnaker\n"
          + "When sending a delete manifest operation with static target\n"
          + "Then the secret is deleted\n===")
  @Test
  public void shouldDeleteByStaticTarget() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    final String kind = "secret";
    final String name = "mysecret";
    Map<String, Object> manifest =
        KubeTestUtils.loadYaml("classpath:manifests/secret.yml")
            .withValue("metadata.namespace", account1Ns)
            .withValue("metadata.name", name)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", manifest);
    // ------------------------- when ---------------------------
    List<Map<String, Object>> request =
        buildStaticRequestBody(String.format("%s %s", kind, name), "true");
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), request, account1Ns);
    // ------------------------- then ---------------------------
    String exist =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, kind, name));
    assertTrue(
        exist.isBlank()
            && deletions.size() == 1
            && deletions.get(0).equals(String.format("%s %s", kind, name)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using newest dynamic target criteria\n"
          + "Then the newest replicaset is deleted\n===")
  @Test
  public void shouldDeleteNewestByDynamicTarget() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String kind = "replicaSet";
    String criteria = "newest";
    String name = String.format("nginx-%s-test", criteria);
    String nameToDelete = String.format("%s-v001", name);
    List<Map<String, Object>> deployManifest =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.name", name)
            .asList();
    List<Map<String, Object>> deployRequest =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", account1Ns)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", deployManifest)
            .asList();
    for (byte i = 0; i < 2; i++) {
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), deployRequest, account1Ns, String.format("replicaSet %s-v%03d", name, i));
    }
    // ------------------------- when ---------------------------
    List<Map<String, Object>> deleteRequest =
        buildDynamicRequestBody(
            String.format("%s %s", kind, nameToDelete),
            "true",
            String.format("%s %s", kind, name),
            criteria,
            kind);
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), deleteRequest, account1Ns);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, kind, nameToDelete));
    assertTrue(
        exists.isBlank()
            && deletions.size() == 1
            && deletions.get(0).equals(String.format("%s %s", kind, nameToDelete)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using second newest dynamic target criteria\n"
          + "Then the second newest replicaset is deleted\n===")
  @Test
  public void shouldDeleteSecondNewestByDynamicTarget() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String kind = "replicaSet";
    String criteria = "second-newest";
    String name = String.format("nginx-%s-test", criteria);
    String nameToDelete = String.format("%s-v000", name);
    List<Map<String, Object>> deployManifest =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.name", name)
            .asList();
    List<Map<String, Object>> deployRequest =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", account1Ns)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", deployManifest)
            .asList();
    for (byte i = 0; i < 2; i++) {
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), deployRequest, account1Ns, String.format("replicaSet %s-v%03d", name, i));
    }
    // ------------------------- when ---------------------------
    List<Map<String, Object>> deleteRequest =
        buildDynamicRequestBody(
            String.format("%s %s", kind, nameToDelete),
            "true",
            String.format("%s %s", kind, name),
            criteria,
            kind);
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), deleteRequest, account1Ns);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, kind, nameToDelete));
    assertTrue(
        exists.isBlank()
            && deletions.size() == 1
            && deletions.get(0).equals(String.format("%s %s", kind, nameToDelete)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using oldest dynamic target criteria\n"
          + "Then the oldest replicaset is deleted\n===")
  @Test
  public void shouldDeleteOldestByDynamicTarget() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String kind = "replicaSet";
    String criteria = "oldest";
    String name = String.format("nginx-%s-test", criteria);
    String nameToDelete = String.format("%s-v000", name);
    List<Map<String, Object>> deployManifest =
        KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
            .withValue("metadata.name", name)
            .asList();
    List<Map<String, Object>> deployRequest =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", ACCOUNT1_NAME)
            .withValue("deployManifest.namespaceOverride", account1Ns)
            .withValue("deployManifest.moniker.app", APP1_NAME)
            .withValue("deployManifest.manifests", deployManifest)
            .asList();
    for (byte i = 0; i < 2; i++) {
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), deployRequest, account1Ns, String.format("replicaSet %s-v%03d", name, i));
    }
    // ------------------------- when ---------------------------
    List<Map<String, Object>> deleteRequest =
        buildDynamicRequestBody(
            String.format("%s %s", kind, nameToDelete),
            "true",
            String.format("%s %s", kind, name),
            criteria,
            kind);
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), deleteRequest, account1Ns);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, kind, nameToDelete));
    assertTrue(
        exists.isBlank()
            && deletions.size() == 1
            && deletions.get(0).equals(String.format("%s %s", kind, nameToDelete)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using largest dynamic target criteria\n"
          + "Then the replicaset that has the greater amount of replicas is deleted\n===")
  @Test
  public void shouldDeleteLargestByDynamicTarget() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String kind = "replicaSet";
    String criteria = "largest";
    String name = String.format("nginx-%s-test", criteria);
    String nameToDelete = String.format("%s-v001", name);
    for (byte i = 0; i < 2; i++) {
      List<Map<String, Object>> deployManifest =
          KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
              .withValue("metadata.name", name)
              .withValue("spec.replicas", i)
              .asList();
      List<Map<String, Object>> deployRequest =
          KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
              .withValue("deployManifest.account", ACCOUNT1_NAME)
              .withValue("deployManifest.namespaceOverride", account1Ns)
              .withValue("deployManifest.moniker.app", APP1_NAME)
              .withValue("deployManifest.manifests", deployManifest)
              .asList();
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), deployRequest, account1Ns, String.format("replicaSet %s-v%03d", name, i));
    }
    // ------------------------- when ---------------------------
    List<Map<String, Object>> deleteRequest =
        buildDynamicRequestBody(
            String.format("%s %s", kind, nameToDelete),
            "true",
            String.format("%s %s", kind, name),
            criteria,
            kind);
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), deleteRequest, account1Ns);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, kind, nameToDelete));
    assertTrue(
        exists.isBlank()
            && deletions.size() == 1
            && deletions.get(0).equals(String.format("%s %s", kind, nameToDelete)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given two replicaset deployed inside of Spinnaker\n"
          + "When sending a delete manifest operation using smallest dynamic target criteria\n"
          + "Then the replicaset that has the lower amount of replicas is deleted\n===")
  @Test
  public void shouldDeleteSmallestByDynamicTarget() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String kind = "replicaSet";
    String criteria = "smallest";
    String name = String.format("nginx-%s-test", criteria);
    String nameToDelete = String.format("%s-v000", name);
    for (byte i = 0; i < 2; i++) {
      List<Map<String, Object>> deployManifest =
          KubeTestUtils.loadYaml("classpath:manifests/replicaset.yml")
              .withValue("metadata.name", name)
              .withValue("spec.replicas", i)
              .asList();
      List<Map<String, Object>> deployRequest =
          KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
              .withValue("deployManifest.account", ACCOUNT1_NAME)
              .withValue("deployManifest.namespaceOverride", account1Ns)
              .withValue("deployManifest.moniker.app", APP1_NAME)
              .withValue("deployManifest.manifests", deployManifest)
              .asList();
      KubeTestUtils.deployAndWaitStable(
          baseUrl(), deployRequest, account1Ns, String.format("replicaSet %s-v%03d", name, i));
    }
    // ------------------------- when ---------------------------
    List<Map<String, Object>> deleteRequest =
        buildDynamicRequestBody(
            String.format("%s %s", kind, nameToDelete),
            "true",
            String.format("%s %s", kind, name),
            criteria,
            kind);
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), deleteRequest, account1Ns);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, kind, nameToDelete));
    assertTrue(
        exists.isBlank()
            && deletions.size() == 1
            && deletions.get(0).equals(String.format("%s %s", kind, nameToDelete)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment manifest with 3 replicas outside of Spinnaker\n"
          + "When sending a delete manifest operation without cascading option enable\n"
          + "Then just the deployment should be removed at once, but the replicaset/pods remain\n===")
  @Test
  public void shouldDeleteWithoutCascading() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String kind = "deployment";
    String name = "myapp";
    Map<String, Object> deployManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", name)
            .withValue("spec.replicas", 3)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", deployManifest);
    kubeCluster.execKubectl(
        String.format(
            "wait %s -n %s %s --for condition=Available=True --timeout=600s",
            kind, account1Ns, name));
    // ------------------------- when ---------------------------
    List<Map<String, Object>> deleteRequest =
        buildStaticRequestBody(String.format("%s %s", kind, name), "false");
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), deleteRequest, account1Ns);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(String.format("-n %s get pods -l=app=%s", account1Ns, name));
    assertTrue(
        deletions.size() == 1
            && deletions.get(0).equals(String.format("%s %s", kind, name))
            && exists.contains("Running"));
  }

  @ParameterizedTest(
      name =
          ".\n===\n"
              + "Given a deployment\n"
              + "When sending a delete manifest operation with cascading={0}\n"
              + "Then the deployment is deleted\n===")
  @ValueSource(strings = {"foreground", "background", "orphan"})
  public void deleteWithValidCascadingValue(String cascadingValue)
      throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String kind = "deployment";
    String name = "myapp";
    Map<String, Object> deployManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", name)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", deployManifest);
    kubeCluster.execKubectl(
        String.format(
            "wait %s -n %s %s --for condition=Available=True --timeout=600s",
            kind, account1Ns, name));

    // ------------------------- when ---------------------------
    List<Map<String, Object>> deleteRequest =
        buildStaticRequestBody(String.format("%s %s", kind, name), cascadingValue);

    // 30 seconds isn't long enough for delete with --cascade=foreground, so
    // allow longer.
    List<String> deletions =
        KubeTestUtils.sendOperation(baseUrl(), deleteRequest, account1Ns, 60, TimeUnit.SECONDS);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(
            String.format("-n %s get deployment --ignore-not-found %s", account1Ns, name));
    assertTrue(exists.isBlank());
    assertEquals(1, deletions.size());
    assertEquals(String.format("%s %s", kind, name), deletions.get(0));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a deployment\n"
          + "When sending a delete manifest operation with invalid cascading value\n"
          + "Then the delete manifest operation fails and the deployment remains\n===")
  @Test
  public void deleteWithInvalidCascadingValue() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    String kind = "deployment";
    String name = "myapp";
    Map<String, Object> deployManifest =
        KubeTestUtils.loadYaml("classpath:manifests/deployment.yml")
            .withValue("metadata.name", name)
            .asMap();
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", deployManifest);
    kubeCluster.execKubectl(
        String.format(
            "wait %s -n %s %s --for condition=Available=True --timeout=600s",
            kind, account1Ns, name));

    // ------------------------- when ---------------------------
    String invalidCascadingValue = "bogus";
    List<Map<String, Object>> deleteRequest =
        buildStaticRequestBody(String.format("%s %s", kind, name), invalidCascadingValue);

    // KubeTestUtils.repeatUntilTrue waits in 5 second increments, and we need
    // to wait at least 10 seconds to get it to try more than once.  Even if it
    // the operation completes more quickly than that, it doesn't happen on the
    // first attempt.
    String status =
        KubeTestUtils.sendOperationExpectFailure(
            baseUrl(), deleteRequest, account1Ns, 10, TimeUnit.SECONDS);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(
            String.format(
                "-n %s get deployment %s -o jsonpath='{.metadata.name}'", account1Ns, name));
    assertThat(status).contains("invalid cascade value (" + invalidCascadingValue + ")");
    assertEquals(name, exists);
  }

  @DisplayName(
      ".\n===\n"
          + "Given a NOT existing static deployment manifest\n"
          + "When sending a delete manifest request\n"
          + "Then it should return any deleted deployment\n===")
  @Test
  public void shouldNotDeleteStaticTarget() throws InterruptedException {
    // ------------------------- given --------------------------
    // ------------------------- when ---------------------------
    List<Map<String, Object>> request = buildStaticRequestBody("deployment notExists", "true");
    // ------------------------- then ---------------------------
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), request, account1Ns);
    assertEquals(0, deletions.size());
  }

  @DisplayName(
      ".\n===\n"
          + "Given a NOT existing dynamic replicaSet manifest\n"
          + "When sending a delete manifest operation using smallest dynamic target criteria\n"
          + "Then it gets a 404 while fetching the manifest\n===")
  @Test
  public void shouldNotFoundDynamicTarget() throws InterruptedException {
    // ------------------------- given --------------------------
    // ------------------------- when ---------------------------
    String kind = "replicaSet";
    String criteria = "smallest";
    String name = String.format("not-exists-%s-test", criteria);
    String nameToDelete = String.format("%s-v000", name);
    String url =
        String.format(
            "%s/manifests/%s/%s/%s %s", baseUrl(), ACCOUNT1_NAME, account1Ns, kind, nameToDelete);
    Response response = given().queryParam("includeEvents", false).get(url);
    // ------------------------- then ---------------------------
    assertEquals(HttpStatus.NOT_FOUND.value(), response.statusCode());
  }

  @DisplayName(
      ".\n===\n"
          + "Given a CRD deployed outside of Spinnaker\n"
          + "When sending a delete manifest operation with CRD static target\n"
          + "Then the CRD is deleted\n===")
  @Test
  public void shouldDeleteCrd() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    final String kind = "customResourceDefinition";
    final String crdName = "crontabs.stable.example.com";
    final Map<String, Object> crdManifest =
        KubeTestUtils.loadYaml("classpath:manifests/crd_v1.yml")
            .withValue("metadata.name", crdName)
            .withValue("spec.scope", "Namespaced")
            .asMap();
    kubeCluster.execKubectl(" apply -f -", crdManifest);
    // ------------------------- when ---------------------------
    List<Map<String, Object>> request =
        buildStaticRequestBody(String.format("%s %s", kind, crdName), "true");
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), request, account1Ns);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(String.format("get %s %s --ignore-not-found", kind, crdName));
    assertTrue(
        exists.isBlank()
            && deletions.size() == 1
            && deletions.get(0).equals(String.format("%s %s", kind, crdName)));
  }

  @DisplayName(
      ".\n===\n"
          + "Given a CR deployed outside of Spinnaker\n"
          + "When sending a delete manifest operation with CR static target\n"
          + "Then the CR is deleted\n===")
  @Test
  public void shouldDeleteCr() throws IOException, InterruptedException {
    // ------------------------- given --------------------------
    final String kind = "crontab.stable.example.com";
    final String crdName = "crontabs.stable.example.com";
    final String crName = "my-new-cron-object";
    final Map<String, Object> crdManifest =
        KubeTestUtils.loadYaml("classpath:manifests/crd_v1.yml")
            .withValue("metadata.name", crdName)
            .withValue("spec.scope", "Namespaced")
            .asMap();
    final Map<String, Object> crManifest =
        KubeTestUtils.loadYaml("classpath:manifests/cr_v1.yml")
            .withValue("metadata.name", crName)
            .asMap();
    kubeCluster.execKubectl(" apply -f -", crdManifest);
    kubeCluster.execKubectl("-n " + account1Ns + " apply -f -", crManifest);
    // ------------------------- when ---------------------------
    List<Map<String, Object>> request =
        buildStaticRequestBody(String.format("%s %s", kind, crName), "true");
    List<String> deletions = KubeTestUtils.sendOperation(baseUrl(), request, account1Ns);
    // ------------------------- then ---------------------------
    String exists =
        kubeCluster.execKubectl(
            String.format("-n %s get %s %s --ignore-not-found", account1Ns, kind, crName));
    assertTrue(
        exists.isBlank()
            && deletions.size() == 1
            && deletions.get(0).equals(String.format("%s %s", kind, crName)));
  }

  private List<Map<String, Object>> buildStaticRequestBody(String manifestName, String cascading) {
    return KubeTestUtils.loadJson("classpath:requests/delete_manifest.json")
        .withValue("deleteManifest.app", APP1_NAME)
        .withValue("deleteManifest.mode", "static")
        .withValue("deleteManifest.manifestName", manifestName)
        .withValue("deleteManifest.options.cascading", cascading)
        .withValue("deleteManifest.location", account1Ns)
        .withValue("deleteManifest.account", ACCOUNT1_NAME)
        .asList();
  }

  private List<Map<String, Object>> buildDynamicRequestBody(
      String manifestName, String cascading, String cluster, String criteria, String kind) {
    return KubeTestUtils.loadJson("classpath:requests/delete_manifest.json")
        .withValue("deleteManifest.app", APP1_NAME)
        .withValue("deleteManifest.mode", "dynamic")
        .withValue("deleteManifest.cluster", cluster)
        .withValue("deleteManifest.criteria", criteria)
        .withValue("deleteManifest.kind", kind)
        .withValue("deleteManifest.manifestName", manifestName)
        .withValue("deleteManifest.options.cascading", cascading)
        .withValue("deleteManifest.location", account1Ns)
        .withValue("deleteManifest.account", ACCOUNT1_NAME)
        .asList();
  }
}
