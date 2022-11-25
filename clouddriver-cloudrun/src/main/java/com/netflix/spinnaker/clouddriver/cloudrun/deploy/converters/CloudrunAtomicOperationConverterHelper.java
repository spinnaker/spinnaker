/*
 * Copyright 2022 OpsMx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.AbstractCloudrunCredentialsDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsConverter;
import java.util.Map;

public class CloudrunAtomicOperationConverterHelper {
  public static <T extends AbstractCloudrunCredentialsDescription> T convertDescription(
      Map input,
      AbstractAtomicOperationsCredentialsConverter<CloudrunNamedAccountCredentials>
          credentialsSupport,
      Class<T> targetDescriptionType) {

    Object accountName = null;
    if (input.get("accountName") != null) {
      accountName = input.get("accountName");
    } else if (input.get("account") != null) {
      accountName = input.get("account");
    } else if (input.get("credentials") != null) {
      accountName = input.get("credentials");
    }

    input.put("accountName", accountName);

    if (input.get("accountName") != null) {
      input.put(
          "credentials",
          credentialsSupport.getCredentialsObject((String) input.get("accountName")));
      input.put("account", (String) input.get("accountName"));
    } else {
      throw new RuntimeException("Could not find Cloud Run account.");
    }

    Object credentials = input.remove("credentials");

    T converted =
        credentialsSupport
            .getObjectMapper()
            .copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .convertValue(input, targetDescriptionType);

    converted.setCredentials((CloudrunNamedAccountCredentials) credentials);
    return converted;
  }
}
