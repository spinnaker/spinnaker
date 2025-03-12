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

import static com.netflix.spinnaker.halyard.config.validate.v1.providers.dockerRegistry.DockerRegistryReferenceValidation.validateDockerRegistries;
import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.ERROR;
import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.FATAL;
import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.WARNING;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.containers.DockerRegistryReference;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.job.v1.JobExecutor;
import com.netflix.spinnaker.halyard.core.job.v1.JobRequest;
import com.netflix.spinnaker.halyard.core.job.v1.JobStatus;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskInterrupted;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
public class KubernetesAccountValidator extends Validator<KubernetesAccount> {
  @Override
  public void validate(ConfigProblemSetBuilder psBuilder, KubernetesAccount account) {
    switch (account.getProviderVersion()) {
        // TODO(mneterval): remove all V1-only validators after 1.23 is released
      case V1:
        addV1RemovalWarning(psBuilder, account);
        validateV1KindConfig(psBuilder, account);
        validateCacheThreads(psBuilder, account);
        validateV1DockerRegistries(psBuilder, account);
        validateOnlySpinnakerConfig(psBuilder, account);
      case V2:
        validateKindConfig(psBuilder, account);
        validateCacheThreads(psBuilder, account);
      default:
        throw new IllegalStateException("Unknown provider version " + account.getProviderVersion());
    }
  }

  private void addV1RemovalWarning(ConfigProblemSetBuilder psBuilder, KubernetesAccount account) {
    psBuilder.addProblem(
        WARNING,
        String.format(
            "Account %s is using Spinnaker’s legacy Kubernetes provider (V1), which is scheduled for removal in Spinnaker 1.21. "
                + "Please migrate to the manifest-based provider (V2). Check out this RFC for more information: "
                + "https://github.com/spinnaker/governance/blob/master/rfc/eol_kubernetes_v1.md.",
            account.getName()));
  }

  private void validateV1DockerRegistries(
      ConfigProblemSetBuilder psBuilder, KubernetesAccount account) {
    Node parent = account.getParent();
    while (!(parent instanceof DeploymentConfiguration)) {
      // Note this will crash in the above check if the halconfig representation is corrupted
      // (that's ok, because it indicates a more serious error than we want to validate).
      parent = parent.getParent();
    }
    DeploymentConfiguration deploymentConfiguration = (DeploymentConfiguration) parent;

    List<String> dockerRegistryNames =
        account.getDockerRegistries().stream()
            .map(DockerRegistryReference::getAccountName)
            .collect(Collectors.toList());

    validateDockerRegistries(
        psBuilder, deploymentConfiguration, dockerRegistryNames, Provider.ProviderType.KUBERNETES);
  }

  private void validateV1KindConfig(ConfigProblemSetBuilder psBuilder, KubernetesAccount account) {
    List<String> kinds = account.getKinds();
    List<String> omitKinds = account.getOmitKinds();
    List<KubernetesAccount.CustomKubernetesResource> customResources = account.getCustomResources();

    if (CollectionUtils.isNotEmpty(kinds)
        || CollectionUtils.isNotEmpty(omitKinds)
        || CollectionUtils.isNotEmpty(customResources)) {
      psBuilder.addProblem(
          WARNING,
          "Kubernetes accounts at V1 do no support configuring caching behavior for kinds or custom resources.");
    }
  }

  private void validateKindConfig(ConfigProblemSetBuilder psBuilder, KubernetesAccount account) {
    List<String> kinds = account.getKinds();
    List<String> omitKinds = account.getOmitKinds();
    List<KubernetesAccount.CustomKubernetesResource> customResources = account.getCustomResources();

    if (CollectionUtils.isNotEmpty(kinds) && CollectionUtils.isNotEmpty(omitKinds)) {
      psBuilder.addProblem(ERROR, "At most one of \"kinds\" and \"omitKinds\" may be specified.");
    }

    if (CollectionUtils.isNotEmpty(customResources)) {
      List<String> kubernetesKindNotSet =
          customResources.stream()
              .map(KubernetesAccount.CustomKubernetesResource::getKubernetesKind)
              .filter(cr -> (cr == null || cr.isEmpty()))
              .collect(Collectors.toList());
      if (CollectionUtils.isNotEmpty(kubernetesKindNotSet)) {
        psBuilder.addProblem(ERROR, "Missing custom resource name (Kubernetes Kind).");
      }
    }

    if (CollectionUtils.isNotEmpty(kinds) && CollectionUtils.isNotEmpty(customResources)) {
      List<String> unmatchedKinds =
          customResources.stream()
              .map(KubernetesAccount.CustomKubernetesResource::getKubernetesKind)
              .filter(cr -> !kinds.contains(cr))
              .collect(Collectors.toList());

      if (CollectionUtils.isNotEmpty(unmatchedKinds)) {
        psBuilder.addProblem(
            WARNING,
            "The following custom resources \""
                + customResources
                + "\" will not be cached since they aren't listed in your existing resource kinds configuration: \""
                + kinds
                + "\".");
      }
    }

    if (CollectionUtils.isNotEmpty(omitKinds) && CollectionUtils.isNotEmpty(customResources)) {
      List<String> matchedKinds =
          customResources.stream()
              .map(KubernetesAccount.CustomKubernetesResource::getKubernetesKind)
              .filter(omitKinds::contains)
              .collect(Collectors.toList());

      if (CollectionUtils.isNotEmpty(matchedKinds)) {
        psBuilder.addProblem(
            WARNING,
            "The following custom resources \""
                + customResources
                + "\" will not be cached since they are listed in you've omitted them in you omitKinds configuration: \""
                + omitKinds
                + "\".");
      }
    }
  }

  private void validateOnlySpinnakerConfig(
      ConfigProblemSetBuilder psBuilder, KubernetesAccount account) {
    Boolean onlySpinnakerManaged = account.getOnlySpinnakerManaged();

    if (onlySpinnakerManaged) {
      psBuilder.addProblem(
          WARNING,
          "Kubernetes accounts at V1 does not support configuring caching behavior for a only spinnaker managed resources.");
    }
  }

  private void validateCacheThreads(ConfigProblemSetBuilder psBuilder, KubernetesAccount account) {
    if (account.getCacheThreads() < 0) {
      psBuilder.addProblem(ERROR, "\"cacheThreads\" should be greater or equal to 0.");
    }
  }

  public void ensureKubectlExists(ConfigProblemSetBuilder p) {
    JobExecutor jobExecutor = DaemonTaskHandler.getJobExecutor();
    JobRequest request =
        new JobRequest()
            .setTokenizedCommand(Collections.singletonList("kubectl"))
            .setTimeoutMillis(TimeUnit.SECONDS.toMillis(10));

    JobStatus status;
    try {
      status = jobExecutor.backoffWait(jobExecutor.startJob(request));
    } catch (InterruptedException e) {
      throw new DaemonTaskInterrupted(e);
    }

    if (status.getResult() != JobStatus.Result.SUCCESS) {
      p.addProblem(
              FATAL,
              String.join(
                  " ",
                  "`kubectl` not installed, or can't be found by Halyard. It is needed for",
                  "opening connections to your Kubernetes cluster to send commands to the Spinnaker deployment running there."))
          .setRemediation(
              String.join(
                  " ",
                  "Visit https://kubernetes.io/docs/tasks/kubectl/install/.",
                  "If you've already installed kubectl via gcloud, it's possible updates to your $PATH aren't visible to Halyard. ",
                  "You might have to restart Halyard for it to pick up the new $PATH."));
    }
  }
}
