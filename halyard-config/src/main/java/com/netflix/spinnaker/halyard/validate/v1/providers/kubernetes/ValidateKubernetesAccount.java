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

package com.netflix.spinnaker.halyard.validate.v1.providers.kubernetes;

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesConfigParser;
import com.netflix.spinnaker.halyard.model.v1.providers.kubernetes.KubernetesAccount;
import com.netflix.spinnaker.halyard.validate.v1.Validator;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.stream.Stream;

public class ValidateKubernetesAccount extends Validator<KubernetesAccount> {
  protected ValidateKubernetesAccount(KubernetesAccount subject) {
    super(subject);
  }

  @Override
  public Stream<String> validate() {
    Config config = KubernetesConfigParser.parse(subject.getKubeconfigFile(),
        subject.getContext(),
        subject.getCluster(),
        subject.getUser(),
        subject.getNamespaces());

    KubernetesClient client = new DefaultKubernetesClient(config);

    try {
      client.namespaces().list();
    } catch (Exception e) {
      return Stream.of("Failed to connect due to " + e.getMessage());
    }

    return null;
  }

  @Override
  public boolean skip() {
    return false;
  }
}
