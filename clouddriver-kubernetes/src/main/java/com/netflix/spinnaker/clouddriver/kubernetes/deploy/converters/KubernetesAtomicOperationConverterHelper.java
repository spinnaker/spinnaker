/*
 * Copyright 2019 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.kubernetes.deploy.converters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesAtomicOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class KubernetesAtomicOperationConverterHelper {
  public static <T extends KubernetesAtomicOperationDescription> T convertDescription(
      Map input,
      AbstractAtomicOperationsCredentialsSupport credentialsSupport,
      Class<T> targetDescriptionType) {
    String account = (String) input.get("account");
    String removedAccount = (String) input.remove("credentials");
    account = StringUtils.isNotEmpty(account) ? account : removedAccount;

    // Save these to re-assign after ObjectMapper does its work.
    KubernetesNamedAccountCredentials credentials =
        credentialsSupport.getCredentialsObject(account);

    T converted =
        credentialsSupport
            .getObjectMapper()
            .copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .convertValue(input, targetDescriptionType);

    // Re-assign the credentials.
    converted.setCredentials(credentials);
    if (StringUtils.isNotEmpty(removedAccount)) {
      input.put("credentials", removedAccount);
      converted.setAccount(removedAccount);
    }

    return converted;
  }
}
