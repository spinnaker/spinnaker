/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.CredentialsChangeable;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

public class OperationConverter<T extends CredentialsChangeable, E extends AtomicOperation<?>>
    extends AbstractAtomicOperationsCredentialsSupport {
  private Function<T, E> constructor;
  private Class<T> clazz;

  public OperationConverter(Function<T, E> constructor, Class<T> clazz) {
    this.constructor = constructor;
    this.clazz = clazz;
  }

  @Nullable
  @Override
  public E convertOperation(Map<String, Object> input) {
    return constructor.apply(convertDescription(input));
  }

  @Override
  public T convertDescription(Map<String, Object> input) {
    return convertDescription(input, this, clazz);
  }

  public T convertDescription(
      Map<String, Object> input,
      AbstractAtomicOperationsCredentialsSupport credentialsSupport,
      Class<T> targetDescriptionType) {

    if (!input.containsKey("accountName")) {
      input.put("accountName", input.get("credentials"));
    }

    if (input.get("accountName") != null) {
      input.put(
          "credentials",
          credentialsSupport.getCredentialsObject(String.valueOf(input.get("accountName"))));
    }

    // Save these to re-assign after ObjectMapper does its work.
    Object credentials = input.remove("credentials");

    T t =
        credentialsSupport
            .getObjectMapper()
            .copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .convertValue(input, targetDescriptionType);
    if (credentials instanceof YandexCloudCredentials) {
      t.setCredentials((YandexCloudCredentials) credentials);
    }
    return t;
  }
}
