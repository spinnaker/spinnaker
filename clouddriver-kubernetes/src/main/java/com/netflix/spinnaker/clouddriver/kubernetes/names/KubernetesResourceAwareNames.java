/*
 * Copyright 2022 Salesforce.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.kubernetes.names;

import com.netflix.frigga.NameConstants;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * the {@link Names} class is used for deconstructing information about AWS Auto Scaling Groups,
 * Load Balancers, Launch Configurations, and Security Groups created by Asgard based on their name.
 *
 * <p>While the above class is mainly used for AWS, it works for most of the Kubernetes resources as
 * well, since Kubernetes resources follow a similar naming convention. But there are certain
 * Kubernetes resources which are reserved for Kubernetes system use that have the prefix "system:".
 * See <a
 * href="https://kubernetes.io/docs/reference/access-authn-authz/rbac/#referring-to-subjects">Referring
 * to subjects</a> for more details.
 *
 * <p>One such use of these resources is in {@link KubernetesKind#CLUSTER_ROLE} kind, which you can
 * attempt to patch via Spinnaker. Because the {@link Names} class cannot parse this name, Spinnaker
 * fails to return the manifest. This class is used to handle such resources in addition to all the
 * other resources.
 */
@Getter
public class KubernetesResourceAwareNames {

  /**
   * identifier for the special system resources. See <a
   * href="https://kubernetes.io/docs/reference/access-authn-authz/rbac/#referring-to-subjects">Referring
   * to subjects</a> for more details
   */
  private static final String KUBERNETES_SYSTEM_RESOURCE_PREFIX = "system:";

  /**
   * A regex pattern to figure out if the manifest name has a version present in it. Used to obtain
   * the sequence number for those resources that have the {@link
   * KubernetesResourceAwareNames#KUBERNETES_SYSTEM_RESOURCE_PREFIX}.
   */
  private static final Pattern PUSH_PATTERN =
      Pattern.compile(
          "^([" + KUBERNETES_SYSTEM_RESOURCE_PREFIX + "].*)-(" + NameConstants.PUSH_FORMAT + ")$");

  /**
   * It gets the value from {@link Names#parseName(String)} for most of the Kubernetes resources.
   * But for manifests with {@link
   * KubernetesResourceAwareNames#KUBERNETES_SYSTEM_RESOURCE_PREFIX},it gets it from {@link
   * KubernetesResourceAwareNames#parseName(String)}.
   *
   * <p>For example, if manifest name = system:coredns, then cluster = system:coredns.
   *
   * <p>if manifest name = system:coredns-v003, then cluster = system:coredns.
   *
   * <p>If manifest name = test-abc, cluster = test-abc
   *
   * <p>If manifest name = test-abc-v003, cluster = test-abc
   *
   * <p>See <a
   * href="https://github.com/Netflix/frigga/blob/master/src/test/groovy/com/netflix/frigga/NamesSpec.groovy">
   * ParseName Examples</a> for more details.
   */
  private final String cluster;

  /**
   * The Spinnaker Application to which this resource belongs to.
   *
   * <p>It gets the value from {@link Names#parseName(String)} for most of the Kubernetes resources.
   * But for manifests with {@link KubernetesResourceAwareNames#KUBERNETES_SYSTEM_RESOURCE_PREFIX},
   * it gets it from {@link KubernetesResourceAwareNames#parseName(String)}.
   *
   * <p>For example, if manifest name = system:coredns, then app = system.
   *
   * <p>If manifest name = test-abc, app = test
   *
   * <p>See <a
   * href="https://github.com/Netflix/frigga/blob/master/src/test/groovy/com/netflix/frigga/NamesSpec.groovy">
   * ParseName Examples</a> for more details.
   */
  private final String app;

  /**
   * The versioned sequence number of this manifest.
   *
   * <p>It gets the value from {@link Names#parseName(String)} for most of the Kubernetes
   * resources.But for manifests with {@link
   * KubernetesResourceAwareNames#KUBERNETES_SYSTEM_RESOURCE_PREFIX}, it gets it from {@link
   * KubernetesResourceAwareNames#parseName(String)}.
   *
   * <p>For example, if manifest name = system:coredns, then sequence = null.
   *
   * <p>If manifest name = system:coredns-v003, then sequence = 3.
   *
   * <p>If manifest name = test-abc, sequence = null.
   *
   * <p>If manifest name = test-abc-v003, sequence = 3.
   *
   * <p>See <a
   * href="https://github.com/Netflix/frigga/blob/master/src/test/groovy/com/netflix/frigga/NamesSpec.groovy">
   * ParseName Examples</a> for more details.
   */
  private final Integer sequence;

  public KubernetesResourceAwareNames(String cluster, String application, Integer sequence) {
    this.cluster = cluster;
    this.app = application;
    this.sequence = sequence;
  }

  /**
   * parses the given manifestName into a {@link KubernetesResourceAwareNames} object. It handles
   * all types of Kubernetes manifests
   *
   * @param manifestName given manifest name
   * @return {@link KubernetesResourceAwareNames} representation of the manifest name
   */
  public static KubernetesResourceAwareNames parseName(String manifestName) {
    if (manifestName != null && !manifestName.trim().isEmpty()) {
      if (manifestName.startsWith(KUBERNETES_SYSTEM_RESOURCE_PREFIX)) {
        return parseSystemResourceName(manifestName);
      }
    }

    Names parsed = Names.parseName(manifestName);
    return new KubernetesResourceAwareNames(
        parsed.getCluster(), parsed.getApp(), parsed.getSequence());
  }

  /**
   * handles Kubernetes manifests that contain the prefix {@link
   * KubernetesResourceAwareNames#KUBERNETES_SYSTEM_RESOURCE_PREFIX}.
   *
   * @param manifestName given manifest name
   * @return {@link KubernetesResourceAwareNames} representation of the manifest name
   */
  private static KubernetesResourceAwareNames parseSystemResourceName(String manifestName) {
    String[] split = manifestName.split(":");
    Integer sequence = null;
    Matcher pushMatcher = PUSH_PATTERN.matcher(manifestName);
    boolean hasPush = pushMatcher.matches();

    // if manifestName == "system:certificates.k8s.io:certificatesigningrequests:nodeclient-v003",
    // then
    // pushMatcher.group(0) =
    // "system:certificates.k8s.io:certificatesigningrequests:nodeclient-v003",
    // pushMatcher.group(1) = "system:certificates.k8s.io:certificatesigningrequests:nodeclient",
    // pushMatcher.group(2) = "v003",
    // pushMatcher.group(3) = "3"
    String theCluster = hasPush ? pushMatcher.group(1) : manifestName;
    String sequenceString = hasPush ? pushMatcher.group(3) : null;
    if (sequenceString != null) {
      sequence = Integer.parseInt(sequenceString);
    }
    // since this method is called only when the manifest name contains
    // KUBERNETES_SYSTEM_RESOURCE_PREFIX, split[0] will always contain what we need, which
    // is KUBERNETES_SYSTEM_RESOURCE_PREFIX without the ":"
    return new KubernetesResourceAwareNames(theCluster, split[0], sequence);
  }
}
