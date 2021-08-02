/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.config;

import com.github.wnameless.json.unflattener.JsonUnflattener;
import com.netflix.spinnaker.clouddriver.config.AbstractBootstrapCredentialsConfigurationProvider;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * If a configuration properties file has a large number of kubernetes accounts, as-is SpringBoot
 * implementation of properties binding is inefficient. Hence, a custom logic for binding just the
 * {@link KubernetesAccountProperties} is written but it still uses SpringBoot's Binder class.
 * {@link KubernetesCustomAccountConfigurationProvider} class fetches the flattened kubernetes
 * properties from Spring Cloud Config's BootstrapPropertySource and creates an {@link
 * KubernetesAccountProperties} object.
 */
@Slf4j
public class KubernetesCustomAccountConfigurationProvider
    extends AbstractBootstrapCredentialsConfigurationProvider<KubernetesAccountProperties> {
  private final String FIRST_ACCOUNT_NAME_KEY = "kubernetes.accounts[0].name";

  public KubernetesCustomAccountConfigurationProvider(
      ConfigurableApplicationContext applicationContext,
      CloudConfigResourceService configResourceService,
      SecretManager secretManager) {
    super(applicationContext, configResourceService, secretManager);
  }

  @Override
  public KubernetesAccountProperties getConfigurationProperties() {
    return getKubernetesAccounts(getPropertiesMap(FIRST_ACCOUNT_NAME_KEY));
  }

  @SuppressWarnings("unchecked")
  private KubernetesAccountProperties getKubernetesAccounts(
      Map<String, Object> kubernetesPropertiesMap) {
    log.info("Started loading Kubernetes accounts");
    KubernetesAccountProperties accounts = new KubernetesAccountProperties();
    BindResult<?> result;

    // unflatten
    Map<String, Object> propertiesMap =
        (Map<String, Object>)
            JsonUnflattener.unflattenAsMap(kubernetesPropertiesMap).get("kubernetes");

    // loop through each account and bind
    for (Map<String, Object> unflattendAcc :
        ((List<Map<String, Object>>) propertiesMap.get("accounts"))) {
      result = bind(getFlatMap(unflattendAcc), KubernetesAccountProperties.ManagedAccount.class);
      accounts.getAccounts().add((KubernetesAccountProperties.ManagedAccount) result.get());
    }
    log.info("Finished loading kubernetes accounts");
    return accounts;
  }
}
