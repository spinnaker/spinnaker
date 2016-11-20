/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.node;

/**
 * A node filter can be used to inform an iterator which elements to return.
 */
public class NodeFilter extends NodeReference {
  private static final String ANY = "*";

  public static boolean matches(String a, String b) {
    if (a.isEmpty() || b.isEmpty()) {
      return false;
    }

    if (a.equals(ANY) || b.equals(ANY)) {
      return true;
    }

    return a.equals(b);
  }

  public NodeFilter anyHalconfigFile() {
    halconfigFile = ANY;
    return this;
  }

  public NodeFilter anyDeployment() {
    deployment = ANY;
    return this;
  }

  public NodeFilter anyWebhook() {
    webhook = ANY;
    return this;
  }

  public NodeFilter anyProvider() {
    provider = ANY;
    return this;
  }

  public NodeFilter anyAccount() {
    account = ANY;
    return this;
  }

  public NodeFilter anyMaster() {
    master = ANY;
    return this;
  }

  public NodeFilter(NodeReference reference) {
    this.halconfigFile = reference.halconfigFile;
    this.deployment = reference.deployment;
    this.webhook = reference.webhook;
    this.provider = reference.provider;
    this.account = reference.account;
    this.master = reference.master;
  }
}
