/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.halyard.config.validate.v1.providers.kubernetes;

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesConfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.util.ValidatingFileReader;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskInterrupted;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.halyard.config.validate.v1.providers.dockerRegistry.DockerRegistryReferenceValidation.validateDockerRegistries;
import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.*;

@Component
public class KubernetesAccountValidator extends Validator<KubernetesAccount> {
  @Override
  public void validate(ConfigProblemSetBuilder psBuilder, KubernetesAccount account) {
    DeploymentConfiguration deploymentConfiguration;

    // TODO(lwander) this is still a little messy - I should use the filters to get the necessary docker account
    Node parent = account.getParent();
    while (!(parent instanceof DeploymentConfiguration)) {
      // Note this will crash in the above check if the halconfig representation is corrupted
      // (that's ok, because it indicates a more serious error than we want to validate).
      parent = parent.getParent();
    }
    deploymentConfiguration = (DeploymentConfiguration) parent;

    final List<String> dockerRegistryNames = account.getDockerRegistries().stream().map(DockerRegistryReference::getAccountName)
        .collect(Collectors.toList());
    validateDockerRegistries(psBuilder, deploymentConfiguration, dockerRegistryNames, Provider.ProviderType.KUBERNETES);
    validateKubeconfig(psBuilder, account);
  }

  private void validateKubeconfig(ConfigProblemSetBuilder psBuilder, KubernetesAccount account) {
    io.fabric8.kubernetes.api.model.Config kubeconfig;
    String context = account.getContext();
    String kubeconfigFile = account.getKubeconfigFile();
    String cluster = account.getCluster();
    String user = account.getUser() ;
    List<String> namespaces = account.getNamespaces();
    List<String> omitNamespaces = account.getOmitNamespaces();

    // This indicates if a first pass at the config looks OK. If we don't see any serious problems, we'll do one last check
    // against the requested kubernetes cluster to ensure that we can run spinnaker.
    boolean smoketest = true;

    boolean namespacesProvided = namespaces != null && !namespaces.isEmpty();
    boolean omitNamespacesProvided = omitNamespaces != null && !omitNamespaces.isEmpty();

    if (namespacesProvided && omitNamespacesProvided) {
      psBuilder.addProblem(ERROR, "At most one of \"namespaces\" and \"omitNamespaces\" can be supplied.");
      smoketest = false;
    }

    // TODO(lwander) find a good resource / list of resources for generating kubeconfig files to link to here.
    try {
      if (ValidatingFileReader.contents(psBuilder, kubeconfigFile) == null) {
        return;
      }

      File kubeconfigFileOpen = new File(kubeconfigFile);
      kubeconfig = KubeConfigUtils.parseConfig(kubeconfigFileOpen);
    } catch (IOException e) {
      psBuilder.addProblem(ERROR, e.getMessage());
      return;
    }

    System.out.println(context);
    if (context != null && !context.isEmpty()) {
      Optional<NamedContext> namedContext = kubeconfig
          .getContexts()
          .stream()
          .filter(c -> c.getName().equals(context))
          .findFirst();

      if (!namedContext.isPresent()) {
        psBuilder.addProblem(ERROR, "Context \"" + context + "\" not found in kubeconfig \"" + kubeconfigFile + "\".", "context")
            .setRemediation("Either add this context to your kubeconfig, rely on the default context, or pick another kubeconfig file.");
        smoketest = false;
      }
    } else {
      String currentContext = kubeconfig.getCurrentContext();
      if (currentContext.isEmpty()) {
        psBuilder.addProblem(ERROR, "You have not specified a Kubernetes context, and your kubeconfig \"" + kubeconfigFile + "\" has no current-context.", "context")
            .setRemediation("Either specify a context in your halconfig, or set a current-context in your kubeconfig.");
        smoketest = false;
      } else {
        psBuilder.addProblem(WARNING, "You have not specified a Kubernetes context in your halconfig, Spinnaker will use \"" + currentContext + "\" instead.", "context")
            .setRemediation("We recommend explicitly setting a context in your halconfig, to ensure changes to your kubeconfig won't break your deployment.");
      }
    }

    if (smoketest) {
      Config config = KubernetesConfigParser.parse(kubeconfigFile, context, cluster, user, namespaces, false);
      try {
        KubernetesClient client = new DefaultKubernetesClient(config);

        client.namespaces().list();
      } catch (Exception e) {
        ConfigProblemBuilder pb = psBuilder.addProblem(ERROR, "Unable to communicate with your Kubernetes cluster: " + e.getMessage() + ".");

        if (e.getMessage().contains("Token may have expired")) {
          pb.setRemediation("If you downloaded these keys with gcloud, it's possible they are in the wrong format. To fix this, run \n\n"
              + "gcloud config set container/use_client_certificate true\n\ngcloud container clusters get-credentials $CLUSTERNAME");
        } else {
          pb.setRemediation("Unable to authenticate with your Kubernetes cluster. Try using kubectl to verify your credentials.");
        }
      }
    }
  }

  public void ensureKubectlExists(ConfigProblemSetBuilder p) {
    JobExecutor jobExecutor = DaemonTaskHandler.getJobExecutor();
    JobRequest request = new JobRequest()
        .setTokenizedCommand(Collections.singletonList("kubectl"))
        .setTimeoutMillis(TimeUnit.SECONDS.toMillis(10));

    JobStatus status;
    try {
      status = jobExecutor.backoffWait(jobExecutor.startJob(request));
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      p.addProblem(FATAL, String.join(" ", "`kubectl` not installed, or can't be found by Halyard. It is needed for",
          "opening connections to your Kubernetes cluster to send commands to the Spinnaker deployment running there."))
          .setRemediation(String.join(" ", "Visit https://kubernetes.io/docs/tasks/kubectl/install/.",
              "If you've already installed kubectl via gcloud, it's possible updates to your $PATH aren't visible to Halyard."));
    }
  }
}
