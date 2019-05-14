/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.clouddriver.documentation.Empty;
import com.netflix.spinnaker.clouddriver.documentation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * A cluster provider is an interface for the application to retrieve implementations of {@link
 * Cluster} objects. This interface defines the common contract for which various providers may be
 * queried for their known clusters. This interface assumes implementations may span cross account.
 */
public interface ClusterProvider<T extends Cluster> {
  /**
   * Looks up all of the clusters available to this provider. Keyed on account name.
   *
   * @return set of clusters or an empty set if none exist
   */
  @Empty
  Map<String, Set<T>> getClusters();

  /**
   * Looks up all of the clusters known to this provider to be for a specified application Keyed on
   * account name. Similar to {@link #getClusterSummaries(java.lang.String)}, but returns the names
   * of server groups and load balancers, not reified views.
   *
   * @param application
   * @return map of clusters, keyed on account name, or an empty map if none exist
   */
  @Empty
  Map<String, Set<T>> getClusterSummaries(String application);

  /**
   * Looks up all of the clusters known to this provider to be for a specified application Keyed on
   * account name. Similar to {@link #getClusterSummaries(java.lang.String)}, but returns reified
   * views of server groups and load balancers.
   *
   * @param application
   * @return map of clusters, keyed on account name, or an empty map if none exist
   */
  @Empty
  Map<String, Set<T>> getClusterDetails(String application);

  /**
   * Looks up all of the clusters known to this provider to be for a specified application and
   * within a {@link com.netflix.spinnaker.clouddriver.security.AccountCredentials} registered with
   * a {@link com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider}
   *
   * @param application
   * @param account name
   * @return set of clusters with load balancers and server groups populated, or an empty set if
   *     none exist
   */
  @Empty
  Set<T> getClusters(String application, String account);

  @Empty
  default Set<T> getClusters(String application, String account, boolean includeDetails) {
    return getClusters(application, account);
  }

  /**
   * Looks up a cluster known to this provider to be for a specified application, within a specified
   * {@link com.netflix.spinnaker.clouddriver.security.AccountCredentials}, and with the specified
   * name.
   *
   * @param account
   * @param name
   * @return cluster with load balancers and server groups populated, or null if none exists
   */
  @Nullable
  T getCluster(String application, String account, String name);

  @Nullable
  T getCluster(String application, String account, String name, boolean includeDetails);

  /**
   * Looks up a server group known to this provider, within a specified {@link
   * com.netflix.spinnaker.clouddriver.security.AccountCredentials} and region, and with the
   * specified name.
   *
   * @param account name
   * @param region
   * @param name
   * @param includeDetails
   * @return the server group or null if none exists
   */
  @Nullable
  ServerGroup getServerGroup(String account, String region, String name, boolean includeDetails);

  @Nullable
  ServerGroup getServerGroup(String account, String region, String name);

  /** @return the identifier of the backing cloud provider */
  String getCloudProviderId();

  /**
   * Determines whether or not optimizations can be made by retrieving minimal or unexpanded
   * clusters.
   *
   * <p>This primarily affects how server groups are loaded for a cluster (@see
   * com.netflix.spinnaker.clouddriver.controllers.ClusterController}.
   *
   * <p>Minimal cluster support requires that server groups contained within a cluster be populated
   * with: - creation time stamps - region / zone details - disabled status - instance counts (fully
   * populated instances are not necessary)
   */
  boolean supportsMinimalClusters();
}
