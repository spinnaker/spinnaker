/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.kubernetes;

import com.amazonaws.util.IOUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesConfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.AccountDeploymentDetails;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class KubernetesProviderUtils {
  // Map from deployment name -> the port & job managing the connection.
  private static ConcurrentHashMap<String, Proxy> proxyMap = new ConcurrentHashMap<>();

  @Data
  static class Proxy {
    String jobId;
    Integer port;
  }

  static Proxy openProxy(JobExecutor jobExecutor, AccountDeploymentDetails<KubernetesAccount> details) {
    KubernetesAccount account = details.getAccount();
    Proxy proxy = proxyMap.getOrDefault(details.getDeploymentName(), new Proxy());
    if (proxy.jobId == null || proxy.jobId.isEmpty()) {
      DaemonTaskHandler.newStage("Connecting to the Kubernetes cluster in account \"" + account.getName() + "\"");
      List<String> command = kubectlAccountCommand(details);
      command.add("proxy");
      command.add("--port=0"); // select a random port
      JobRequest request = new JobRequest().setTokenizedCommand(command);

      proxy.jobId = jobExecutor.startJob(request);

      // Wait for the proxy to spin up.
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
      } catch (InterruptedException ignored) {
      }

      JobStatus status = jobExecutor.updateJob(proxy.jobId);

      // This should be a long-running job.
      if (status.getState() == JobStatus.State.COMPLETED) {
        throw new HalException(Severity.FATAL,
            "Unable to establish a proxy against account " + account.getName()
                + ":\n" + status.getStdOut() + "\n" + status.getStdErr());
      }

      String connectionMessage = status.getStdOut();
      Pattern portPattern = Pattern.compile(":(\\d+)");
      Matcher matcher = portPattern.matcher(connectionMessage);
      if (matcher.find()) {
        proxy.setPort(Integer.valueOf(matcher.group(1)));
        proxyMap.put(details.getDeploymentName(), proxy);
        DaemonTaskHandler.log("Connected to kubernetes cluster for account "
            + account.getName() + " on port " + proxy.getPort());
        DaemonTaskHandler.log("View the kube ui on http://localhost:" + proxy.getPort() + "/ui/");
      } else {
        throw new HalException(Severity.FATAL,
            "Could not parse connection information from:\n"
                + connectionMessage + "(" + status.getStdErr() + ")");
      }
    }

    return proxy;
  }

  static KubernetesClient getClient(AccountDeploymentDetails<KubernetesAccount> details) {
    KubernetesAccount account = details.getAccount();
    Config config = KubernetesConfigParser.parse(account.getKubeconfigFile(),
        account.getContext(),
        account.getCluster(),
        account.getUser(),
        account.getNamespaces(),
        false);

    return new DefaultKubernetesClient(config);
  }

  static void upsertSecret(AccountDeploymentDetails<KubernetesAccount> details, Set<String> files, String secretName, String namespace) {
    KubernetesClient client = getClient(details);

    if (client.secrets().inNamespace(namespace).withName(secretName).get() != null) {
      client.secrets().inNamespace(namespace).withName(secretName).delete();
    }

    Map<String, String> secretContents = new HashMap<>();

    files.forEach(s -> {
      try {
        File f = new File(s);
        String name = f.getName();
        String data = new String(Base64.getEncoder().encode(IOUtils.toString(new FileInputStream(f)).getBytes()));
        secretContents.putIfAbsent(name, data);
      } catch (IOException e) {
        throw new HalException(Severity.ERROR, "Unable to read contents of \"" + s + "\": " + e);
      }
    });

    SecretBuilder secretBuilder = new SecretBuilder();
    secretBuilder = secretBuilder.withNewMetadata()
        .withName(secretName)
        .withNamespace(namespace)
        .endMetadata()
        .withData(secretContents);

    client.secrets().inNamespace(namespace).create(secretBuilder.build());
  }

  static void createNamespace(AccountDeploymentDetails<KubernetesAccount> details, String namespace) {
    KubernetesClient client = getClient(details);
    Map<String, String> annotations = new HashMap<>();
    annotations.put("net.beta.kubernetes.io/network-policy", "{\"ingress\": {\"isolation\": \"DefaultDeny\"}}");
    if (client.namespaces().withName(namespace).get() == null) {
      client.namespaces().create(new NamespaceBuilder()
          .withNewMetadata()
          .withName(namespace)
          .withAnnotations(annotations)
          .endMetadata()
          .build());
    } else {
      client.namespaces()
          .withName(namespace)
          .edit()
          .withNewMetadata()
          .withAnnotations(annotations)
          .withName(namespace)
          .endMetadata()
          .done();
    }
  }

  static void deleteReplicaSet(AccountDeploymentDetails<KubernetesAccount> details, String namespace, String name) {
    getClient(details).extensions().replicaSets().inNamespace(namespace).withName(name).delete();
  }

  static String componentSecret(String name) {
    return "hal-" + name + "-config";
  }

  static String componentMonitoring(String name) {
    return "hal-" + name + "-monitoring";
  }

  static String componentRegistry(String name) {
    return "hal-" + name + "-registry";
  }

  static String componentDependencies(String name) {
    return "hal-" + name + "-dependencies";
  }

  static List<String> kubectlPortForwardCommand(AccountDeploymentDetails<KubernetesAccount> details, String namespace, String instance, int port) {
    List<String> command =  kubectlAccountCommand(details);
    command.add("--namespace");
    command.add(namespace);

    command.add("port-forward");
    command.add(instance);

    command.add(port + "");
    return command;
  }

  private static List<String> kubectlAccountCommand(AccountDeploymentDetails<KubernetesAccount> details) {
    KubernetesAccount account = details.getAccount();
    List<String> command = new ArrayList<>();
    command.add("kubectl");

    String context = account.getContext();
    if (context != null && !context.isEmpty()) {
      command.add("--context");
      command.add(context);
    }

    String cluster = account.getCluster();
    if (cluster != null && !cluster.isEmpty()) {
      command.add("--cluster");
      command.add(cluster);
    }

    String user = account.getUser();
    if (user != null && !user.isEmpty()) {
      command.add("--user");
      command.add(user);
    }

    String kubeconfig = account.getKubeconfigFile();
    if (kubeconfig != null && !kubeconfig.isEmpty()) {
      command.add("--kubeconfig");
      command.add(kubeconfig);
    }

    return command;
  }
}
