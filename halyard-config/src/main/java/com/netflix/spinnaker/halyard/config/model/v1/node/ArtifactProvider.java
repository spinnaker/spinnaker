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
 *
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class ArtifactProvider<A extends ArtifactAccount> extends Node {
  boolean enabled = false;
  List<A> accounts = new ArrayList<>();

  private boolean hasAccount(String name) {
    return accounts.stream().anyMatch(a -> a.getName().equals(name));
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeListIterator(
        accounts.stream().map(a -> (Node) a).collect(Collectors.toList()));
  }

  @Override
  public String getNodeName() {
    return providerType().getName();
  }

  public abstract ProviderType providerType();

  public enum ProviderType {
    BITBUCKET("bitbucket"),
    GCS("gcs"),
    ORACLE("oracle"),
    GITHUB("github"),
    GITLAB("gitlab"),
    GITREPO("gitrepo"),
    HELM("helm"),
    HTTP("http"),
    S3("s3"),
    MAVEN("maven");

    @Getter private final String name;

    ProviderType(String name) {
      this.name = name;
    }
  }
}
