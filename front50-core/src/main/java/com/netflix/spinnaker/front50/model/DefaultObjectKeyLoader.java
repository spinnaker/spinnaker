/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.front50.model;

import java.util.Map;

public class DefaultObjectKeyLoader implements ObjectKeyLoader {
  private final StorageService storageService;

  public DefaultObjectKeyLoader(StorageService storageService) {
    this.storageService = storageService;
  }

  @Override
  public Map<String, Long> listObjectKeys(ObjectType objectType) {
    return storageService.listObjectKeys(objectType);
  }
}
