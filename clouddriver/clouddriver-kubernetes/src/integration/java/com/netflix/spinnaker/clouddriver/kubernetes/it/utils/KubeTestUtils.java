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

package com.netflix.spinnaker.clouddriver.kubernetes.it.utils;

import static com.netflix.spinnaker.clouddriver.kubernetes.it.BaseTest.ACCOUNT1_NAME;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.netflix.spinnaker.clouddriver.kubernetes.it.containers.KubernetesCluster;
import com.netflix.spinnaker.kork.yaml.YamlHelper;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.testcontainers.utility.ComparableVersion;
import org.yaml.snakeyaml.Yaml;

public abstract class KubeTestUtils {

  private static final int SLEEP_STEP_SECONDS = 5;

  public static TestResourceFile loadYaml(String file) {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    try {
      InputStream is = resourceLoader.getResource(file).getInputStream();
      Yaml yaml = YamlHelper.newYamlSafeConstructor();
      Iterable<Object> contentIterable = yaml.loadAll(is);
      List<Map<String, Object>> content =
          StreamSupport.stream(contentIterable.spliterator(), false)
              .filter(Objects::nonNull)
              .map(KubeTestUtils::coerceManifestToList)
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
      return new TestResourceFile(content);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to load manifest from file " + file, e);
    }
  }

  private static List<Map<String, Object>> coerceManifestToList(Object manifest) {
    ObjectMapper objectMapper = new ObjectMapper();
    if (manifest instanceof List) {
      return objectMapper.convertValue(manifest, new TypeReference<>() {});
    }
    Map<String, Object> singleManifest =
        objectMapper.convertValue(manifest, new TypeReference<>() {});
    return Collections.singletonList(singleManifest);
  }

  public static TestResourceFile loadJson(String file) {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    try {
      InputStream is = resourceLoader.getResource(file).getInputStream();
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode jsonNode = objectMapper.readTree(is);
      List<Map<String, Object>> content;
      if (jsonNode.isArray()) {
        content = objectMapper.convertValue(jsonNode, new TypeReference<>() {});
      } else {
        content =
            Collections.singletonList(
                objectMapper.convertValue(jsonNode, new TypeReference<>() {}));
      }
      return new TestResourceFile(content);
    } catch (IOException e) {
      throw new RuntimeException("Unable to load manifest from file " + file, e);
    }
  }

  public static void repeatUntilTrue(
      BooleanSupplier func, long duration, TimeUnit unit, String errorMsg)
      throws InterruptedException {
    long durationSeconds = unit.toSeconds(duration);
    for (int i = 0; i < (durationSeconds / SLEEP_STEP_SECONDS); i++) {
      if (!func.getAsBoolean()) {
        Thread.sleep(TimeUnit.SECONDS.toMillis(SLEEP_STEP_SECONDS));
      } else {
        return;
      }
    }
    fail(errorMsg);
  }

  public static class TestResourceFile {

    private final List<Map<String, Object>> content;

    public TestResourceFile(List<Map<String, Object>> content) {
      this.content = content;
    }

    public List<Map<String, Object>> asList() {
      return content;
    }

    public Map<String, Object> asMap() {
      return content.get(0);
    }

    @SuppressWarnings("unchecked")
    public TestResourceFile withValue(String path, Object value) {
      List<String> parts = Splitter.on('.').splitToList(path);

      for (Map<String, Object> entry : content) {
        for (int i = 0; i < parts.size(); i++) {
          if (parts.get(i).matches("^.*\\[[0-9]*]$")) {
            String key = parts.get(i).substring(0, parts.get(i).indexOf('['));
            int index =
                Integer.parseInt(
                    parts
                        .get(i)
                        .substring(parts.get(i).indexOf('[') + 1, parts.get(i).indexOf(']')));
            List<Map<String, Object>> list = (List<Map<String, Object>>) entry.get(key);
            if (i == parts.size() - 1) {
              list.add(index, (Map<String, Object>) value);
              break;
            }
            entry = list.get(index);
          } else if (i == parts.size() - 1) {
            entry.put(parts.get(i), value);
            break;
          } else if (!entry.containsKey(parts.get(i))) {
            entry.put(parts.get(i), new HashMap<>());
            entry = (Map<String, Object>) entry.get(parts.get(i));
          } else {
            entry = (Map<String, Object>) entry.get(parts.get(i));
          }
        }
      }

      return this;
    }
  }

  public static void deployAndWaitStable(
      String baseUrl, List<Map<String, Object>> reqBody, String targetNs, String... objectNames)
      throws InterruptedException {

    System.out.println("> Sending deploy manifest request for " + Arrays.toString(objectNames));
    List<String> deployedObjectNames = sendOperation(baseUrl, reqBody, targetNs);

    Arrays.sort(objectNames);
    Collections.sort(deployedObjectNames);
    assertEquals(
        Arrays.asList(objectNames),
        deployedObjectNames,
        "Expected object names deployed: "
            + Arrays.toString(objectNames)
            + " but were: "
            + deployedObjectNames);

    for (String objectName : objectNames) {
      System.out.println(
          "> Sending get manifest request for object \"" + objectName + "\" to check stability");
      long start = System.currentTimeMillis();
      KubeTestUtils.repeatUntilTrue(
          () -> {
            String url =
                baseUrl
                    + "/manifests/"
                    + ACCOUNT1_NAME
                    + "/"
                    + (targetNs.isBlank() ? "default" : targetNs)
                    + "/"
                    + objectName;
            System.out.println("GET " + url);
            Response respWait = given().queryParam("includeEvents", false).get(url);
            JsonPath jsonPath = respWait.jsonPath();
            System.out.println(jsonPath.getObject("status", Map.class));
            respWait.then().statusCode(200).body("status.failed.state", is(false));
            return jsonPath.getBoolean("status.stable.state");
          },
          5,
          TimeUnit.MINUTES,
          "Waited 5 minutes on GET /manifest.. to return \"status.stable.state: true\"");
      System.out.println(
          "< Object \""
              + objectName
              + "\" stable in "
              + ((System.currentTimeMillis() - start) / 1000)
              + " seconds");
    }
  }

  public static void disableManifest(
      String baseUrl, List<Map<String, Object>> reqBody, String targetNs, String objectName)
      throws InterruptedException {
    System.out.println("> Sending disable manifest request for " + objectName);
    sendOperation(baseUrl, reqBody, targetNs);
  }

  public static List<String> sendOperation(
      String baseUrl, List<Map<String, Object>> reqBody, String targetNs)
      throws InterruptedException {
    return sendOperation(baseUrl, reqBody, targetNs, 30, TimeUnit.SECONDS);
  }

  public static List<String> sendOperation(
      String baseUrl,
      List<Map<String, Object>> reqBody,
      String targetNs,
      long duration,
      TimeUnit unit)
      throws InterruptedException {

    String taskId = submitOperation(baseUrl, reqBody);

    List<String> deployedObjectNames = new ArrayList<>();
    processOperation(
        baseUrl,
        taskId,
        duration,
        unit,
        (Response respTask) -> {
          respTask.then().body("status.failed", is(false));
          deployedObjectNames.addAll(
              respTask
                  .jsonPath()
                  .getList(
                      "resultObjects.manifestNamesByNamespace."
                          + (targetNs.isBlank() ? "''" : targetNs)
                          + ".flatten()",
                      String.class));
        });

    return deployedObjectNames;
  }

  public static String sendOperationExpectFailure(
      String baseUrl,
      List<Map<String, Object>> reqBody,
      String targetNs,
      long duration,
      TimeUnit unit)
      throws InterruptedException {
    String taskId = submitOperation(baseUrl, reqBody);

    // The last status is most interesting, but a single String isn't
    // effectively final and so can't be used in a lambda.  So, use a list.  Any
    // kind of wrapper object would do.
    List<String> status = new ArrayList<>();
    processOperation(
        baseUrl,
        taskId,
        duration,
        unit,
        (Response respTask) -> {
          respTask.then().body("status.failed", is(true));
          status.add(respTask.jsonPath().getString("status.status"));
        });

    // Return the status to the caller so it's possible to assert on it.
    return Iterables.getOnlyElement(status);
  }

  /** Wait until a task has completed, calling a consumer with the response once it has. */
  public static void processOperation(
      String baseUrl, String taskId, long duration, TimeUnit unit, Consumer<Response> consumer)
      throws InterruptedException {
    System.out.println("> Waiting for task to complete");
    long start = System.currentTimeMillis();

    // So it's possible to capture the Response from the lambda passed to
    // repeatUntilTrue, uese a wrapper.  List is an arbitrary choice.
    List<Response> respList = new ArrayList();

    KubeTestUtils.repeatUntilTrue(
        () -> {
          Response respTask = getTask(baseUrl, taskId);
          if (respTask == null) {
            return false;
          }
          respList.add(respTask);
          return true;
        },
        duration,
        unit,
        "Waited "
            + duration
            + " "
            + unit
            + " on GET /task/{id} to return \"status.completed: true\"");
    System.out.println(
        "< Task completed in " + ((System.currentTimeMillis() - start) / 1000) + " seconds");

    consumer.accept(Iterables.getOnlyElement(respList));
  }

  /**
   * Submit an operation to clouddriver
   *
   * @return the task id
   */
  public static String submitOperation(String baseUrl, List<Map<String, Object>> reqBody) {
    String url = baseUrl + "/kubernetes/ops";
    System.out.println("POST " + url);
    Response resp =
        given().log().body(false).contentType("application/json").body(reqBody).post(url);
    System.out.println(resp.asString());
    resp.then().statusCode(200);
    System.out.println("< Completed in " + resp.getTimeIn(TimeUnit.SECONDS) + " seconds");
    return resp.jsonPath().get("id");
  }

  /**
   * Get a task from clouddriver
   *
   * @return the response from the get task method, if the task has completed, or null if the task
   *     was not found, or the task hasn't completed yet.
   */
  public static Response getTask(String baseUrl, String taskId) {
    String taskUrl = baseUrl + "/task/" + taskId;
    System.out.println("GET " + taskUrl);
    Response respTask = given().get(taskUrl);
    if (respTask.statusCode() == 404) {
      return null;
    }
    respTask.then().statusCode(200);
    System.out.println(respTask.jsonPath().getObject("status", Map.class));

    if (!respTask.jsonPath().getBoolean("status.completed")) {
      return null;
    }

    return respTask;
  }

  @SuppressWarnings("unchecked")
  public static void forceCacheRefresh(String baseUrl, String targetNs, String objectName)
      throws InterruptedException {
    System.out.println("> Sending force cache refresh request for object \"" + objectName + "\"");
    Response resp =
        given()
            .log()
            .uri()
            .contentType("application/json")
            .body(
                ImmutableMap.of(
                    "account", ACCOUNT1_NAME,
                    "location", targetNs,
                    "name", objectName))
            .post(baseUrl + "/cache/kubernetes/manifest");
    resp.then().statusCode(anyOf(is(200), is(202)));
    System.out.println("< Completed in " + resp.getTimeIn(TimeUnit.SECONDS) + " seconds");

    if (resp.statusCode() == 202) {
      System.out.println("> Waiting cache to be refreshed for object \"" + objectName + "\"");
      long start = System.currentTimeMillis();
      KubeTestUtils.repeatUntilTrue(
          () -> {
            Response fcrWaitResp = given().log().uri().get(baseUrl + "/cache/kubernetes/manifest");
            fcrWaitResp.then().log().body(false);
            List<Object> list =
                Stream.of(fcrWaitResp.as(Map[].class))
                    .filter(
                        it -> {
                          Map<String, Object> details = (Map<String, Object>) it.get("details");
                          String name = (String) details.get("name");
                          String account = (String) details.get("account");
                          String location = (String) details.get("location");
                          Number processedTime = (Number) it.get("processedTime");
                          return Objects.equals(ACCOUNT1_NAME, account)
                              && Objects.equals(targetNs, location)
                              && Objects.equals(objectName, name)
                              && processedTime != null
                              && processedTime.longValue() > -1;
                        })
                    .collect(Collectors.toList());
            return !list.isEmpty();
          },
          5,
          TimeUnit.MINUTES,
          "GET /cache/kubernetes/manifest did not returned processedTime > -1 for object \""
              + objectName
              + "\" after 5 minutes");
      System.out.println(
          "< Force cache refresh for \""
              + objectName
              + "\" completed in "
              + ((System.currentTimeMillis() - start) / 1000)
              + " seconds");
    } else {
      System.out.println(
          "< Force cache refresh for object \"" + objectName + "\" succeeded immediately");
    }
  }

  public static void deployAndWaitStable(
      String baseUrl, String account, String namespace, String kind, String name, String app)
      throws InterruptedException {
    deployAndWaitStable(baseUrl, account, namespace, kind, name, app, null);
  }

  public static void deployAndWaitStable(
      String baseUrl,
      String account,
      String namespace,
      String kind,
      String name,
      String app,
      String image)
      throws InterruptedException {

    TestResourceFile manifest =
        KubeTestUtils.loadYaml("classpath:manifests/" + kind + ".yml")
            .withValue("metadata.name", name)
            .withValue("metadata.namespace", namespace);
    if (image != null) {
      manifest = manifest.withValue("spec.template.spec.containers[0].image", image);
    }
    List<Map<String, Object>> body =
        KubeTestUtils.loadJson("classpath:requests/deploy_manifest.json")
            .withValue("deployManifest.account", account)
            .withValue("deployManifest.moniker.app", app)
            .withValue("deployManifest.manifests", manifest.asList())
            .asList();
    KubeTestUtils.deployAndWaitStable(baseUrl, body, namespace, kind + " " + name);
  }

  public static void deployIfMissing(
      String baseUrl,
      String account,
      String namespace,
      String kind,
      String name,
      String app,
      KubernetesCluster kubeCluster)
      throws InterruptedException, IOException {
    deployIfMissing(baseUrl, account, namespace, kind, name, app, null, kubeCluster);
  }

  public static void deployIfMissing(
      String baseUrl,
      String account,
      String namespace,
      String kind,
      String name,
      String app,
      String image,
      KubernetesCluster kubeCluster)
      throws InterruptedException, IOException {

    String path = "";
    if (image != null) {
      path = ".spec.template.spec.containers[0].image";
    }

    String output =
        kubeCluster.execKubectl(
            "-n "
                + namespace
                + " get "
                + kind
                + " -l app.kubernetes.io/name="
                + app
                + " -o=jsonpath='{.items[?(@.metadata.name==\""
                + name
                + "\")]"
                + path
                + "}'");
    if (!output.isEmpty()) {
      if (image == null || output.contains(image)) {
        return;
      }
    }

    deployAndWaitStable(baseUrl, account, namespace, kind, name, app, image);
  }

  public static int compareVersion(String sv1, String sv2) {
    ComparableVersion v1 = new ComparableVersion(sv1.replace("v", ""));
    ComparableVersion v2 = new ComparableVersion(sv2.replace("v", ""));

    return v1.compareTo(v2);
  }
}
