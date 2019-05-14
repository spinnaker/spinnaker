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

package com.netflix.spinnaker.clouddriver.appengine.artifacts.config;

import groovy.transform.ToString;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;

@Data
@ConfigurationProperties("artifacts.gcs")
public class StorageConfigurationProperties {
  @Data
  @ToString(includeNames = true)
  public static class ManagedAccount {
    String name;
    String jsonPath;

    public static String responseToString(Response response) {
      return new String(((TypedByteArray) response.getBody()).getBytes());
    }
  }

  ManagedAccount getAccount(String name) {
    for (ManagedAccount account : accounts) {
      if (account.getName().equals(name)) {
        return account;
      }
    }
    throw new NoSuchElementException("Unknown storage account: " + name);
  }

  List<ManagedAccount> accounts = new ArrayList<ManagedAccount>();
}
