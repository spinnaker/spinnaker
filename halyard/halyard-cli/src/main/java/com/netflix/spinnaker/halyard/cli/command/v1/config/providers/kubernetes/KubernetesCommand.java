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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.kubernetes;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractNamedProviderCommand;

/** Interact with the kubernetes provider */
@Parameters(separators = "=")
public class KubernetesCommand extends AbstractNamedProviderCommand {
  protected String getProviderName() {
    return "kubernetes";
  }

  @Override
  protected String getLongDescription() {
    return String.join(
        "",
        "The Kubernetes provider is used to deploy Kubernetes resources to any number of Kubernetes clusters. ",
        "Spinnaker assumes you have a Kubernetes cluster already running. If you don't, you must configure one: ",
        "https://kubernetes.io/docs/getting-started-guides/. \n\nBefore proceeding, please visit ",
        "https://kubernetes.io/docs/concepts/cluster-administration/authenticate-across-clusters-kubeconfig/ ",
        "to make sure you're familiar with the authentication terminology. For more information on how to ",
        "configure individual accounts, or how to deploy to multiple clusters, please read the documentation ",
        "under `hal config provider kubernetes account -h`.");
  }

  public KubernetesCommand() {
    super();
    registerSubcommand(new KubernetesAccountCommand());
    registerSubcommand(new KubernetesEditProviderCommand());
  }
}
