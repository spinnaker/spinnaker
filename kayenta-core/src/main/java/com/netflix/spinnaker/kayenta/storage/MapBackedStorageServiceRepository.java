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

package com.netflix.spinnaker.kayenta.storage;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MapBackedStorageServiceRepository implements StorageServiceRepository {

  @Autowired(required = false)
  List<StorageService> storageServices = Collections.emptyList();

  @Override
  public Optional<StorageService> getOne(String accountName) {
    return storageServices
      .stream()
      .filter(s -> s.servicesAccount(accountName))
      .findFirst();
  }
}
