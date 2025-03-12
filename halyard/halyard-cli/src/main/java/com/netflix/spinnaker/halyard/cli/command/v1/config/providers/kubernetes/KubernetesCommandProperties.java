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

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.kubernetes;

public class KubernetesCommandProperties {
  static final String CONTEXT_DESCRIPTION =
      "The kubernetes context to be managed by Spinnaker. "
          + "See http://kubernetes.io/docs/user-guide/kubeconfig-file/#context for more information.\n"
          + "When no context is configured for an account the 'current-context' in your kubeconfig is assumed.";

  static final String NAMESPACES_DESCRIPTION =
      "A list of namespaces this Spinnaker account can deploy to and will cache.\n"
          + "When no namespaces are configured, this defaults to 'all namespaces'.";

  static final String OMIT_NAMESPACES_DESCRIPTION =
      "A list of namespaces this Spinnaker account cannot deploy to or cache.\n"
          + "This can only be set when --namespaces is empty or not set.";

  static final String KINDS_DESCRIPTION =
      "(V2 Only) A list of resource kinds this Spinnaker account can deploy to and will cache.\n"
          + "When no kinds are configured, this defaults to 'all kinds described here https://spinnaker.io/reference/providers/kubernetes-v2/'.";

  static final String OMIT_KINDS_DESCRIPTION =
      "(V2 Only) A list of resource kinds this Spinnaker account cannot deploy to or cache.\n"
          + "This can only be set when --kinds is empty or not set.";

  static final String DOCKER_REGISTRIES_DESCRIPTION =
      "A list of the Spinnaker docker registry account names this Spinnaker account can use as image sources. "
          + "These docker registry accounts must be registered in your halconfig before you can add them here.";

  static final String KUBECONFIG_DESCRIPTION =
      "The path to your kubeconfig file. By default, it will be under the Spinnaker user's home directory in the typical "
          + ".kube/config location.";

  static final String SERVICE_ACCOUNT_DESCRIPTION =
      "When true, Spinnaker attempt to authenticate against Kubernetes using a Kubernetes service account. "
          + "This only works when Halyard & Spinnaker are deployed in Kubernetes. Read more about service accounts here: https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/.";

  static final String CONFIGURE_IMAGE_PULL_SECRETS_DESCRIPTION =
      "(Only applicable to the v1 provider). When true, Spinnaker will create & manage your image pull "
          + "secrets for you; when false, you will have to create and attach them to your pod specs by hand.";

  static final String ONLY_SPINNAKER_MANAGED_DESCRIPTION =
      "(V2 Only) When true, Spinnaker will only cache/display applications that have been\n"
          + "created by Spinnaker; as opposed to attempting to configure applications for resources already present in Kubernetes.";

  static final String CHECK_PERMISSIONS_ON_STARTUP =
      "When false, clouddriver will skip the permission checks for all kubernetes kinds at startup. This can save a great deal of time\n"
          + "during clouddriver startup when you have many kubernetes accounts configured. This disables the log messages at startup about missing permissions.";

  static final String LIVE_MANIFEST_CALLS =
      "When true, clouddriver will query manifest status during pipeline executions using live data rather than the cache.\n"
          + "This eliminates all time spent in the \"force cache refresh\" task in pipelines, greatly reducing execution time.";

  static final String CACHE_THREADS =
      "Number of caching agents for this kubernetes account. Each agent handles a subset of the namespaces available to this account. "
          + "By default, only 1 agent caches all kinds for all namespaces in the account.";

  static final String CACHE_INTERVAL_SECONDS_DESCRIPTION =
      "How many seconds elapse between polling your kubernetes api. Kubernetes apis are sensitive to over-polling, and "
          + "larger intervals (e.g. 10 minutes = 600 seconds) are desirable if you're seeing rate limiting.";

  static final String CACHE_ALL_APPLICATION_RELATIONSHIPS =
      "If true, will add application relationships in the cache for all types of resources.\n"
          + "This includes CRDs and resources that aren't used in the Clusters, Load Balancers or Firewalls sections.";

  static final String CUSTOM_RESOURCES =
      "(V2 Only) Add Kubernetes custom resource to the list of custom resources to managed by clouddriver and made available for use in patch and delete manifest stages. "
          + "Fields besides the Kubernetes Kind (resource name) can be set using the flags \"--spinnaker-kind\" and \"--versioned\"";

  static final String RAW_RESOURCES_ENDPOINT_KIND_EXPRESSIONS =
      "(V2 Only) A list of resource kind regular expressions that the raw resources endpoint will use to filter resources. "
          + "Only resources matching one or more of the provided expressions will be returned.";

  static final String RAW_RESOURCES_ENDPOINT_OMIT_KIND_EXPRESSIONS =
      "(V2 Only) A list of resource kind regular expressions that the raw resources endpoint will use to filter resources. "
          + "Only resources that don't match any of the provided expressions will be returned. "
          + "This can only be set when --raw-resource-endpoint-kinds is empty or not set";

  static final String PROVIDER_VERSION_DESCRIPTION =
      "There are currently two versions of the Kubernetes Provider: V1 and V2. "
          + "This allows you to pick the version of the provider (not the resources it manages) to run within Spinnaker."
          + "V1 is scheduled for removal in Spinnaker 1.21; we recommend using V2 only.";
}
