/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.igor.docker;

import javax.annotation.Nullable;

public class DockerRegistryV1Key {
  private final String prefix;
  private final String id;
  private final String account;
  private final String registry;

  @Nullable private final String repository;
  private final String tag;

  public DockerRegistryV1Key(
      String prefix, String id, String account, String registry, String repository, String tag) {
    this.prefix = prefix;
    this.id = id;
    this.account = account;
    this.registry = registry;
    this.repository = repository;
    this.tag = tag;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getId() {
    return id;
  }

  public String getAccount() {
    return account;
  }

  public String getRegistry() {
    return registry;
  }

  @Nullable
  public String getRepository() {
    return repository;
  }

  public String getTag() {
    return tag;
  }

  public String toString() {
    return String.format("%s:%s:%s:%s:%s:%s", prefix, id, account, registry, repository, tag);
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof DockerRegistryV1Key && toString().equals(obj.toString());
  }
}
