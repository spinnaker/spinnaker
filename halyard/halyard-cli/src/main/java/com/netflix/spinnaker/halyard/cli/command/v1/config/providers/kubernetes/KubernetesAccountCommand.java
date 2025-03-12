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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.kubernetes;

import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.providers.AbstractAccountCommand;

/** Interact with the kubernetes provider's accounts */
@Parameters(separators = "=")
public class KubernetesAccountCommand extends AbstractAccountCommand {
  protected String getProviderName() {
    return "kubernetes";
  }

  @Override
  protected String getLongDescription() {
    return String.join(
        "",
        "An account in the Kubernetes provider refers to a single Kubernetes context. In Kubernetes, a context ",
        "is the combination of a Kubernetes cluster and some credentials. If no context is specified, the default context in ",
        "in your kubeconfig is assumed.\n\nYou must also provide a set of Docker Registries for each account. ",
        "Spinnaker will automatically upload that Registry's credentials to the specified Kubernetes cluster ",
        "allowing you to deploy those images without further configuration.");
  }

  public KubernetesAccountCommand() {
    super();
    registerSubcommand(new KubernetesAddAccountCommand());
    registerSubcommand(new KubernetesEditAccountCommand());
  }
}
