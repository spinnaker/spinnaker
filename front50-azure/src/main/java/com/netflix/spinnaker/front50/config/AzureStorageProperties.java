/*
 * Copyright 2017 Microsoft, Inc.
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

package com.netflix.spinnaker.front50.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spinnaker.azs")
public class AzureStorageProperties {
  private String storageAccountKey;
  private String storageAccountName;
  private String storageContainerName;

  public String getStorageConnectionString() {
    return "DefaultEndpointsProtocol=https;"
        + "AccountName="
        + this.storageAccountName
        + ";"
        + "AccountKey="
        + this.storageAccountKey;
  }

  public String getStorageAccountKey() {
    return this.storageAccountKey;
  }

  public void setStorageAccountKey(String storageAccountKey) {
    this.storageAccountKey = storageAccountKey;
  }

  public String getStorageAccountName() {
    return this.storageAccountName;
  }

  public void setStorageAccountName(String storageAccountName) {
    this.storageAccountName = storageAccountName;
  }

  public String getStorageContainerName() {
    return this.storageContainerName;
  }

  public void setStorageContainerName(String storageContainerName) {
    this.storageContainerName = storageContainerName;
  }
}
