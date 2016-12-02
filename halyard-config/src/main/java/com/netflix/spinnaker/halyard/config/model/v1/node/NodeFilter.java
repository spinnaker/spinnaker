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

  public NodeFilter withAnyHalconfigFile() {
    halconfigFile = ANY;
    return this;
  }

  public NodeFilter withAnyDeployment() {
    deployment = ANY;
    return this;
  }

  public NodeFilter withAnyWebhook() {
    webhook = ANY;
    return this;
  }

  public NodeFilter withAnyProvider() {
    provider = ANY;
    return this;
  }

  public NodeFilter withAnyAccount() {
    account = ANY;
    return this;
  }

  public NodeFilter withAnyMaster() {
    master = ANY;
    return this;
  }

  /**
   * Modifies the current filter to accept anything the reference does.
   *
   * @param reference is the reference to accept.
   */
  public NodeFilter refineWithReference(NodeReference reference) {
    halconfigFile = definedOrDefault(reference.halconfigFile, halconfigFile);
    deployment = definedOrDefault(reference.deployment, deployment);
    webhook = definedOrDefault(reference.webhook, webhook);
    provider = definedOrDefault(reference.provider, provider);
    account = definedOrDefault(reference.account, account);
    master = definedOrDefault(reference.master, master);

    return this;
  }

  public static NodeFilter makeAcceptAllFilter() {
    NodeFilter result = new NodeFilter();

    result.halconfigFile = ANY;
    result.deployment = ANY;
    result.webhook = ANY;
    result.provider = ANY;
    result.account = ANY;
    result.master = ANY;

    return result;
  }

  public static NodeFilter makeEmptyFilter() {
    return new NodeFilter();
  }

  private NodeFilter() { }

  private static String definedOrDefault(String a, String b) {
    return a != null && !a.isEmpty() ? a : b;
  }
}
