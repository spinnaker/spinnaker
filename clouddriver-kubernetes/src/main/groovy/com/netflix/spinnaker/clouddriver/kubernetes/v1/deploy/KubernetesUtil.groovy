/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy

import com.netflix.frigga.NameValidation
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.KubernetesImageDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesIllegalArgumentException
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import io.fabric8.kubernetes.api.model.Job
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet
import org.springframework.beans.factory.annotation.Value

class KubernetesUtil {
  static String SECURITY_GROUP_LABEL_PREFIX = "security-group-"
  static String LOAD_BALANCER_LABEL_PREFIX = "load-balancer-"
  static String SERVER_GROUP_LABEL = "replication-controller"
  static String DEPRECATED_SERVER_GROUP_KIND = "ReplicationController"
  static String SERVER_GROUP_KIND = "ReplicaSet"
  static String DEPLOYMENT_KIND = "Deployment"
  static String JOB_LABEL = "job"
  static String CONTROLLERS_STATEFULSET_KIND = "StatefulSet"
  static String CONTROLLERS_DAEMONSET_KIND = "DaemonSet"
  @Value("kubernetes.defaultRegistry:gcr.io")
  static String DEFAULT_REGISTRY
  private static int SECURITY_GROUP_LABEL_PREFIX_LENGTH = SECURITY_GROUP_LABEL_PREFIX.length()
  private static int LOAD_BALANCER_LABEL_PREFIX_LENGTH = LOAD_BALANCER_LABEL_PREFIX.length()

  static String ENABLE_DISABLE_ANNOTATION = "service.spinnaker.io/enabled"

  static String getNextSequence(String clusterName, String namespace, KubernetesV1Credentials credentials) {
    def maxSeqNumber = -1
    def replicationControllers = credentials.apiAdaptor.getReplicationControllers(namespace)

    replicationControllers.forEach( { replicationController ->
      def names = Names.parseName(replicationController.getMetadata().getName())

      if (names.cluster == clusterName) {
        maxSeqNumber = Math.max(maxSeqNumber, names.sequence)
      }
    })

    String.format("%03d", ++maxSeqNumber)
  }

  static List<String> getImagePullSecrets(ReplicationController rc) {
    rc.spec?.template?.spec?.imagePullSecrets?.collect({ it.name })
  }

  private static String extractRegistry(String image, KubernetesImageDescription description) {
    def index = image.indexOf('/')
    // No slash means we only provided a repository name & optional tag.
    if (index >= 0) {
      def sPrefix = image.substring(0, index)

      // Check if the content before the slash is a registry (either localhost, or a URL)
      if (sPrefix.startsWith('localhost') || sPrefix.contains('.')) {
        description.registry = sPrefix
        image = image.substring(index + 1)
      }
    }
    image
  }

  private static String extractDigestOrTag(String image, KubernetesImageDescription description) {
    def digestIndex = image.indexOf('@')
    if (digestIndex >= 0) {
      description.digest = image.substring(digestIndex + 1)
      image = image.substring(0, digestIndex)
    } else {
      def tagIndex = image.indexOf(':')
      if (tagIndex >= 0) {
        description.tag = image.substring(tagIndex + 1)
        image = image.substring(0, tagIndex)
      }
    }
    image
  }

  private static void populateFieldsFromUri(KubernetesImageDescription image) {
    def uri = image.uri
    if (uri) {
      uri = extractRegistry(uri, image)
      uri = extractDigestOrTag(uri, image)
      // The repository is what's left after extracting the registry, and digest/tag
      image.repository = uri
    }
  }

  static KubernetesImageDescription buildImageDescription(String image) {
    def result = new KubernetesImageDescription()
    result.uri = image
    normalizeImageDescription(result)
    result
  }

  static Void normalizeImageDescription(KubernetesImageDescription image) {
    populateFieldsFromUri(image)

    if (!image.registry) {
      image.registry = DEFAULT_REGISTRY
    }

    if (!image.tag && !image.digest) {
      image.tag = "latest"
    }

    if (!image.repository) {
      throw new IllegalArgumentException("Image descriptions must provide a repository.")
    }
  }

  static String getImageId(KubernetesImageDescription image) {
    return getImageId(image.registry, image.repository, image.tag, image.digest)
  }

  static String getImageId(String registry, String repository, String tag, String digest) {
    def tagSuffix = digest ? "@$digest" : ":$tag"
    if (registry) {
      return "$registry/$repository$tagSuffix".toString()
    } else {
      return "$repository$tagSuffix".toString()
    }
  }

  static getImageIdWithoutRegistry(KubernetesImageDescription image) {
    def tagSuffix = image.digest ? "@$image.digest" : ":$image.tag"
    "$image.repository$tagSuffix".toString()
  }

  static String validateNamespace(KubernetesV1Credentials credentials, String namespace) {
    def resolvedNamespace = namespace ?: "default"
    if (!credentials.isRegisteredNamespace(resolvedNamespace)) {
      def error = "Registered namespaces are ${credentials.getDeclaredNamespaces()}."
      if (namespace) {
        error = "Namespace '$namespace' was not registered with provided credentials. $error"
      } else {
        error = "No provided namespace assumed to mean 'default' was not registered with provided credentials. $error"
      }
      throw new KubernetesIllegalArgumentException(error)
    }
    return resolvedNamespace
  }

  static Map<String, String> getPodLoadBalancerStates(Pod pod) {
    pod.metadata?.labels?.collectEntries { key, val ->
      if (isLoadBalancerLabel(key)) {
        return [(key): val]
      } else {
        return [:]
      }
    } as Map<String, String> // Groovy resolves [:] as type ?CaptureOf, which is odd since key/val are clearly strings
  }

  static List<String> getLoadBalancers(Map<String, String> labels) {
    labels.findResults { key, val ->
      if (isLoadBalancerLabel(key)) {
        return key.substring(LOAD_BALANCER_LABEL_PREFIX_LENGTH, key.length())
      } else {
        return null
      }
    }
  }

  static List<String> getLoadBalancers(Pod pod) {
    return getLoadBalancers(pod.metadata?.labels ?: [:])
  }

  static List<String> getLoadBalancers(ReplicaSet rs) {
    return getLoadBalancers(rs.spec?.template?.metadata?.labels ?: [:])
  }

  static List<String> getLoadBalancers(ReplicationController rc) {
    return getLoadBalancers(rc.spec?.template?.metadata?.labels ?: [:])
  }

  static List<String> getLoadBalancers(Job job) {
    return getLoadBalancers(job.spec?.template?.metadata?.labels ?: [:])
  }

  static Boolean isLoadBalancerLabel(String key) {
    key.startsWith(LOAD_BALANCER_LABEL_PREFIX)
  }

  static String loadBalancerKey(String loadBalancer) {
    return String.format("$LOAD_BALANCER_LABEL_PREFIX%s".toString(), loadBalancer)
  }

  static String combineAppStackDetail(String appName, String stack, String detail) {
    NameValidation.notEmpty(appName, "appName");

    // Use empty strings, not null references that output "null"
    stack = stack != null ? stack : "";

    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }

    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }

    return appName;
  }
}
