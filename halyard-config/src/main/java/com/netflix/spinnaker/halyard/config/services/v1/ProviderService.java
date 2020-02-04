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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleBMCSProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This service is meant to be autowired into any service or controller that needs to inspect the
 * current halconfigs providers.
 */
@Component
public class ProviderService {
  @Autowired private LookupService lookupService;

  @Autowired private ValidateService validateService;

  @Autowired private DeploymentService deploymentService;

  public HasImageProvider getHasImageProvider(String deploymentName, String providerName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setProvider(providerName);
    Provider provider = getProvider(deploymentName, providerName);
    if (provider instanceof HasImageProvider) {
      return (HasImageProvider) provider;
    } else {
      throw new IllegalConfigException(
          new ConfigProblemBuilder(
                  Severity.FATAL,
                  "Provider \""
                      + providerName
                      + "\" does not support configuring images via Halyard.")
              .build());
    }
  }

  public Provider getProvider(String deploymentName, String providerName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setProvider(providerName);

    List<Provider> matching = lookupService.getMatchingNodesOfType(filter, Provider.class);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Severity.FATAL, "No provider with name \"" + providerName + "\" could be found")
                .setRemediation("Create a new provider with name \"" + providerName + "\"")
                .build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Severity.FATAL,
                    "More than one provider with name \"" + providerName + "\" found")
                .setRemediation(
                    "Manually delete or rename duplicate providers with name \""
                        + providerName
                        + "\" in your halconfig file")
                .build());
    }
  }

  public List<Provider> getAllProviders(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).withAnyProvider();

    List<Provider> matching = lookupService.getMatchingNodesOfType(filter, Provider.class);

    if (matching.size() == 0) {
      throw new ConfigNotFoundException(
          new ConfigProblemBuilder(Severity.FATAL, "No providers could be found").build());
    } else {
      return matching;
    }
  }

  public void setProvider(String deploymentName, Provider provider) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    Providers providers = deploymentConfiguration.getProviders();
    switch (provider.providerType()) {
      case APPENGINE:
        providers.setAppengine((AppengineProvider) provider);
        break;
      case AWS:
        providers.setAws((AwsProvider) provider);
        break;
      case AZURE:
        providers.setAzure((AzureProvider) provider);
        break;
      case DCOS:
        providers.setDcos((DCOSProvider) provider);
        break;
      case DOCKERREGISTRY:
        providers.setDockerRegistry((DockerRegistryProvider) provider);
        break;
      case GOOGLE:
        providers.setGoogle((GoogleProvider) provider);
        break;
      case HUAWEICLOUD:
        providers.setHuaweicloud((HuaweiCloudProvider) provider);
        break;
      case KUBERNETES:
        providers.setKubernetes((KubernetesProvider) provider);
        break;
      case ORACLE:
        providers.setOracle((OracleProvider) provider);
        break;
      case ORACLEBMCS:
        providers.setOraclebmcs((OracleBMCSProvider) provider);
        break;
      case TENCENTCLOUD:
        providers.setTencentcloud((TencentCloudProvider) provider);
        break;
      default:
        throw new IllegalArgumentException("Unknown provider type " + provider.providerType());
    }
  }

  public void setEnabled(String deploymentName, String providerName, boolean enabled) {
    Provider provider = getProvider(deploymentName, providerName);
    provider.setEnabled(enabled);
  }

  public ProblemSet validateProvider(String deploymentName, String providerName) {
    NodeFilter filter =
        new NodeFilter()
            .setDeployment(deploymentName)
            .setProvider(providerName)
            .withAnyAccount()
            .setBakeryDefaults()
            .withAnyBaseImage();

    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validateAllProviders(String deploymentName) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).withAnyProvider().withAnyAccount();

    return validateService.validateMatchingFilter(filter);
  }

  public HasClustersProvider getHasClustersProvider(String deploymentName, String providerName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setProvider(providerName);
    Provider provider = getProvider(deploymentName, providerName);
    if (provider instanceof HasClustersProvider) {
      return (HasClustersProvider) provider;
    } else {
      throw new IllegalConfigException(
          new ConfigProblemBuilder(
                  Severity.FATAL,
                  "Provider \""
                      + providerName
                      + "\" does not support configuring clusters via Halyard.")
              .build());
    }
  }
}
