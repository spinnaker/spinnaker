/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.google.model;

import com.google.api.services.compute.model.Instance;
import com.netflix.spinnaker.clouddriver.consul.model.ConsulNode;
import com.netflix.spinnaker.clouddriver.consul.provider.ConsulProviderUtils;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleInstanceHealth;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import java.math.BigInteger;
import java.util.Optional;

public final class GoogleInstances {

  public static GoogleInstance createFromComputeInstance(
      Instance input,
      GoogleNamedAccountCredentials credentials,
      ServiceClientProvider serviceClientProvider) {

    String localZone = Utils.getLocalName(input.getZone());

    GoogleInstance output = new GoogleInstance();
    output.setName(input.getName());
    output.setAccount(credentials.getProject());
    output.setGceId(Optional.ofNullable(input.getId()).map(BigInteger::toString).orElse(null));
    output.setInstanceType(Utils.getLocalName(input.getMachineType()));
    output.setCpuPlatform(input.getCpuPlatform());
    output.setLaunchTime(calculateInstanceTimestamp(input));
    output.setZone(localZone);
    output.setRegion(credentials.regionFromZone(localZone));
    output.setNetworkInterfaces(input.getNetworkInterfaces());
    output.setNetworkName(calculateNetworkName(input, credentials));
    output.setMetadata(input.getMetadata());
    output.setDisks(input.getDisks());
    output.setServiceAccounts(input.getServiceAccounts());
    output.setSelfLink(input.getSelfLink());
    output.setTags(input.getTags());
    output.setLabels(input.getLabels());
    output.setConsulNode(calculateConsulNode(input, credentials, serviceClientProvider));
    output.setInstanceHealth(createInstanceHealth(input));
    return output;
  }

  private static long calculateInstanceTimestamp(Instance input) {
    return input.getCreationTimestamp() != null
        ? Utils.getTimeFromTimestamp(input.getCreationTimestamp())
        : Long.MAX_VALUE;
  }

  private static String calculateNetworkName(
      Instance input, GoogleNamedAccountCredentials credentials) {
    return Utils.decorateXpnResourceIdIfNeeded(
        credentials.getProject(),
        input.getNetworkInterfaces() != null && !input.getNetworkInterfaces().isEmpty()
            ? input.getNetworkInterfaces().get(0).getNetwork()
            : null);
  }

  private static ConsulNode calculateConsulNode(
      Instance input,
      GoogleNamedAccountCredentials credentials,
      ServiceClientProvider serviceClientProvider) {
    return credentials.getConsulConfig() != null && credentials.getConsulConfig().isEnabled()
        ? ConsulProviderUtils.getHealths(
            credentials.getConsulConfig(), input.getName(), serviceClientProvider)
        : null;
  }

  private static GoogleInstanceHealth createInstanceHealth(Instance input) {
    if (input.getStatus() == null) {
      return null;
    }
    GoogleInstanceHealth health = new GoogleInstanceHealth();
    health.setStatus(GoogleInstanceHealth.Status.valueOf(input.getStatus()));
    return health;
  }
}
