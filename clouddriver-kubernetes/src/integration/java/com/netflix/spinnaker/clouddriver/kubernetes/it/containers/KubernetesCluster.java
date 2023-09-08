/*
 * Copyright Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.it.containers;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.gson.Gson;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.util.FileCopyUtils;

public class KubernetesCluster {

  private static KubernetesCluster INSTANCE;
  private static final String IMAGE = System.getenv("IMAGE");
  private static final String KIND_VERSION = "0.20.0";
  private static final String KUBECTL_VERSION = "1.22.17";
  private static final Path IT_BUILD_HOME = Paths.get(System.getenv("IT_BUILD_HOME"));
  private static final Path KUBECFG_PATH = Paths.get(IT_BUILD_HOME.toString(), "kubecfg.yml");
  private static final Path KUBECTL_PATH = Paths.get(IT_BUILD_HOME.toString(), "kubectl");

  private final Map<String, List<String>> namespacesByAccount = new HashMap<>();

  public static KubernetesCluster getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new KubernetesCluster();
    }
    return INSTANCE;
  }

  private KubernetesCluster() {}

  public void start() {
    try {
      downloadDependencies();
      createCluster();
    } catch (Exception e) {
      fail("Unable to start kubernetes cluster", e);
    }
  }

  public void stop() {
    try {
      runKindCmd("delete cluster --name=kube-int-tests");
    } catch (Exception e) {
      System.out.println("Exception deleting test cluster: " + e.getMessage() + " ignoring");
    }
  }

  public Path getKubecfgPath() {
    return KUBECFG_PATH;
  }

  public String createNamespace(String accountName) throws IOException, InterruptedException {
    List<String> existing =
        namespacesByAccount.computeIfAbsent(accountName, k -> new ArrayList<>());
    String newNamespace = String.format("%s-testns%02d", accountName, existing.size());
    List<String> allNamespaces =
        Arrays.asList(execKubectl("get ns -o=jsonpath='{.items[*].metadata.name}'").split(" "));
    if (!allNamespaces.contains(newNamespace)) {
      execKubectl("create ns " + newNamespace);
    }
    existing.add(newNamespace);
    return newNamespace;
  }

  public String execKubectl(String args) throws IOException, InterruptedException {
    return execKubectl(args, null);
  }

  public String execKubectl(String args, Map<String, Object> manifest)
      throws IOException, InterruptedException {
    String json = manifestToJson(manifest);
    ProcessBuilder builder = new ProcessBuilder();
    List<String> cmd = new ArrayList<>();
    cmd.add("sh");
    cmd.add("-c");
    cmd.add(KUBECTL_PATH + " --kubeconfig=" + KUBECFG_PATH + " " + args);
    builder.command(cmd);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    if (json != null) {
      OutputStream os = process.getOutputStream();
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, UTF_8));
      writer.write(json);
      writer.flush();
      writer.close();
    }
    Reader reader = new InputStreamReader(process.getInputStream(), UTF_8);
    String output = FileCopyUtils.copyToString(reader);
    if (!process.waitFor(1, TimeUnit.MINUTES)) {
      fail("Command %s did not return after one minute", cmd);
    }
    assertThat(process.exitValue())
        .as("Running %s returned non-zero exit code. Output:\n%s", cmd, output)
        .isEqualTo(0);
    System.out.println("kubectl " + args + ":\n" + output);
    return output.trim();
  }

  private String manifestToJson(Map<String, Object> contents) {
    return Optional.ofNullable(contents).map(v -> new Gson().toJson(v)).orElse(null);
  }

  private void downloadDependencies() throws IOException {
    Files.createDirectories(IT_BUILD_HOME);
    String os = "linux";
    String arch = "amd64";
    // TODO: Support running tests in other os/archs
    if (System.getProperty("os.name").toLowerCase().contains("mac")) {
      os = "darwin";
    }
    System.out.println("Detected os: " + os + " arch: " + arch);

    Path kind = Paths.get(IT_BUILD_HOME.toString(), "kind");
    if (!kind.toFile().exists()) {
      String url =
          String.format(
              "https://github.com/kubernetes-sigs/kind/releases/download/v%s/kind-%s-%s",
              KIND_VERSION, os, arch);
      System.out.println("Downloading kind from " + url);
      downloadFile(kind, url);
    }

    Path kubectl = Paths.get(IT_BUILD_HOME.toString(), "kubectl");
    if (!kubectl.toFile().exists()) {
      String url =
          String.format(
              "https://storage.googleapis.com/kubernetes-release/release/v%s/bin/%s/%s/kubectl",
              KUBECTL_VERSION, os, arch);
      System.out.println("Downloading kubectl from " + url);
      downloadFile(kubectl, url);
    }
  }

  private void downloadFile(Path binary, String url) throws IOException {
    try (InputStream is = new URL(url).openStream();
        ReadableByteChannel rbc = Channels.newChannel(is);
        FileOutputStream fos = new FileOutputStream(binary.toFile())) {
      fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
      fos.flush();
      assertThat(binary.toFile().setExecutable(true, false)).isEqualTo(true);
    }
  }

  private void createCluster() throws IOException, InterruptedException {
    String clusters = runKindCmd("get clusters");
    if (clusters.contains("kube-int-tests")) {
      System.out.println("Deleting old test cluster");
      runKindCmd("delete cluster --name=kube-int-tests");
    }
    runKindCmd(
        "create cluster --name=kube-int-tests --kubeconfig="
            + KUBECFG_PATH
            + " --wait=10m --image="
            + IMAGE);
  }

  private String runKindCmd(String args) throws IOException, InterruptedException {
    ProcessBuilder builder = new ProcessBuilder();
    List<String> cmd = new ArrayList<>();
    cmd.add("sh");
    cmd.add("-c");
    cmd.add(Paths.get(IT_BUILD_HOME.toString(), "kind") + " " + args);
    builder.command(cmd);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    Reader reader = new InputStreamReader(process.getInputStream(), UTF_8);
    String output = FileCopyUtils.copyToString(reader);
    System.out.println(output);
    process.waitFor();
    assertThat(process.exitValue())
        .as("Running %s returned non-zero exit code. Output:\n%s", cmd, output)
        .isEqualTo(0);
    return output;
  }
}
