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

package com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import io.fabric8.kubernetes.api.model.Config;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity.ERROR;

@Data
@EqualsAndHashCode(callSuper = false)
public class KubernetesAccount extends Account implements Cloneable {
  String context;
  String cluster;
  String user;
  List<String> namespaces = new ArrayList<>();
  List<DockerRegistryReference> dockerRegistries = new ArrayList<>();
  @LocalFile String kubeconfigFile;

  public String getKubeconfigFile() {
    if (kubeconfigFile == null || kubeconfigFile.isEmpty()) {
      return System.getProperty("user.home") + "/.kube/config";
    } else {
      return kubeconfigFile;
    }
  }

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  protected List<String> contextOptions(ConfigProblemSetBuilder psBuilder) {
    Config kubeconfig;
    try {
      File kubeconfigFileOpen = new File(getKubeconfigFile());
      kubeconfig = KubeConfigUtils.parseConfig(kubeconfigFileOpen);
    } catch (IOException e) {
      psBuilder.addProblem(ERROR, e.getMessage());
      return null;
    }

    return kubeconfig.getContexts()
        .stream()
        .map(NamedContext::getName)
        .collect(Collectors.toList());
  }

  protected List<String> dockerRegistriesOptions(ConfigProblemSetBuilder psBuilder) {
    DeploymentConfiguration context = parentOfType(DeploymentConfiguration.class);
    DockerRegistryProvider dockerRegistryProvider = context.getProviders().getDockerRegistry();

    if (dockerRegistryProvider != null) {
      return dockerRegistryProvider
          .getAccounts()
          .stream()
          .map(Account::getName)
          .collect(Collectors.toList());
    } else {
      return null;
    }
  }
}
