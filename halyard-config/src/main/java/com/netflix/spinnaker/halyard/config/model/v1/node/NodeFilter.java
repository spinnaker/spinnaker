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

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * A way to identify a spot in your halconfig.
 */
@Data
public class NodeFilter implements Cloneable {
  String halconfigFile = "";
  String deployment = "";
  String provider = "";
  String webhook = "";
  String account = "";
  String master = "";
  String configNode = ""; // TODO(lwander) this is temporary until a better nodefilter is written

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

  public NodeFilter withAnyConfigNode() {
    configNode = ANY;
    return this;
  }

  @Override
  public String toString() {
    List<String> res = new ArrayList<>();

    if (!deployment.isEmpty()) {
      res.add(deployment);
    }

    if (!provider.isEmpty()) {
      res.add(provider);
    }

    if (!account.isEmpty()) {
      res.add(account);
    }

    if (!webhook.isEmpty()) {
      res.add(webhook);
    }

    if (!master.isEmpty()) {
      res.add(master);
    }

    if (!configNode.isEmpty()) {
      res.add(configNode);
    }

    return res.stream().reduce("", (a, b) -> a + "." + b).substring(1);
  }
}
