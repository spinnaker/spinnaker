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

import lombok.Setter;

/**
 * A node filter can be used to inform an iterator which elements to return.
 */
public class NodeFilter extends NodeCoordinates {
  private static final String ANY = "*";

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

  public boolean matches(Node node) {
    switch (node.getNodeType()) {
      case DEPLOYMENT:
        return deployment.equals(ANY) || deployment.equals(node.getNodeName());
      case PROVIDER:
        return provider.equals(ANY) || provider.equals(node.getNodeName());
      case ACCOUNT:
        return account.equals(ANY) || account.equals(node.getNodeName());
      case WEBHOOK:
        return webhook.equals(ANY) || webhook.equals(node.getNodeName());
      case ROOT:
        // The root node alone doesn't provide enough information to decide what to filter out.
        return true;
      case LIST:
        // We say a list matches to continue our search, since a list alone doesn't give enough information.
        return true;
      default:
        throw new RuntimeException("Unknown node type encountered: " + node.getNodeType());
    }
  }
}
