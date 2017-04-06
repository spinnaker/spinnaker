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
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Features;
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.openstack.OpenstackAccount;
import com.netflix.spinnaker.halyard.config.model.v1.providers.openstack.OpenstackProvider;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.core.resource.v1.StringResource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DeckProfileFactory extends RegistryBackedProfileFactory {

  @Autowired
  AccountService accountService;

  @Override
  public String commentPrefix() {
    return "// ";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.DECK;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    StringResource configTemplate = new StringResource(profile.getBaseContents());

    Features features = deploymentConfiguration.getFeatures();
    Map<String, String> bindings = new HashMap<>();

    // Configure global settings
    bindings.put("gate.baseUrl", endpoints.getServices().getGate().getBaseUrl());
    bindings.put("timezone", deploymentConfiguration.getTimezone());
    bindings.put("version", deploymentConfiguration.getVersion());

    // Configure feature-flags
    bindings.put("features.auth", Boolean.toString(features.isAuth(deploymentConfiguration)));
    bindings.put("features.chaos", Boolean.toString(features.isChaos()));
    bindings.put("features.fiat", Boolean.toString(features.isFiat()));
    bindings.put("features.jobs", Boolean.toString(features.isJobs()));

    // Configure Kubernetes
    KubernetesProvider kubernetesProvider = deploymentConfiguration.getProviders().getKubernetes();
    bindings.put("kubernetes.default.account", kubernetesProvider.getPrimaryAccount());
    bindings.put("kubernetes.default.namespace", "default");
    bindings.put("kubernetes.default.proxy", "localhost:8001");

    // Configure GCE
    GoogleProvider googleProvider = deploymentConfiguration.getProviders().getGoogle();
    bindings.put("google.default.account", googleProvider.getPrimaryAccount());
    bindings.put("google.default.region", "us-central1");
    bindings.put("google.default.zone", "us-central1-f");

    // Configure Appengine
    AppengineProvider appengineProvider = deploymentConfiguration.getProviders().getAppengine();
    bindings.put("appengine.default.account", appengineProvider.getPrimaryAccount());
    bindings.put("appengine.enabled", Boolean.toString(appengineProvider.getPrimaryAccount() != null));

    // Configure Openstack
    OpenstackProvider openstackProvider = deploymentConfiguration.getProviders().getOpenstack();
    bindings.put("openstack.default.account", openstackProvider.getPrimaryAccount());
    if (openstackProvider.getPrimaryAccount() != null) {
      OpenstackAccount openstackAccount = (OpenstackAccount) accountService.getProviderAccount(deploymentConfiguration.getName(), "openstack", openstackProvider.getPrimaryAccount());
      //Regions in openstack are a comma separated list. Use the first as primary.
      String firstRegion = StringUtils.substringBefore(openstackAccount.getRegions(), ",");
      bindings.put("openstack.default.region", firstRegion);
    }

    profile.appendContents(configTemplate.setBindings(bindings).toString());
  }
}
