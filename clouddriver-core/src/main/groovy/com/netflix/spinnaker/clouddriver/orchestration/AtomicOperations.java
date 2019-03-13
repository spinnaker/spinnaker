/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration;

/**
 * A class that holds the names of ALL the kato operations as constants
 *
 */
public final class AtomicOperations {

  // Server Group operations
  public static final String CLONE_SERVER_GROUP = "cloneServerGroup";
  public static final String CREATE_SERVER_GROUP = "createServerGroup";
  public static final String DISABLE_SERVER_GROUP = "disableServerGroup";
  public static final String ENABLE_SERVER_GROUP = "enableServerGroup";
  public static final String DESTROY_SERVER_GROUP = "destroyServerGroup";
  public static final String RESIZE_SERVER_GROUP = "resizeServerGroup";
  public static final String UPSERT_SERVER_GROUP_TAGS = "upsertServerGroupTags";
  public static final String UPDATE_LAUNCH_CONFIG = "updateLaunchConfig";
  public static final String UPSERT_SCALING_POLICY = "upsertScalingPolicy";
  public static final String DELETE_SCALING_POLICY = "deleteScalingPolicy";
  public static final String MIGRATE_SERVER_GROUP = "migrateServerGroup";
  public static final String MIGRATE_CLUSTER_CONFIGURATIONS = "migrateClusterConfigurations";
  public static final String START_SERVER_GROUP = "startServerGroup";
  public static final String STOP_SERVER_GROUP = "stopServerGroup";

  // Instance operations
  public static final String REBOOT_INSTANCES = "rebootInstances";
  public static final String TERMINATE_INSTANCES = "terminateInstances";
  public static final String TERMINATE_INSTANCE_AND_DECREMENT = "terminateInstanceAndDecrementServerGroup";
  public static final String ATTACH_CLASSIC_LINK_VPC = "attachClassicLinkVpc";
  public static final String REGISTER_INSTANCES_WITH_LOAD_BALANCER = "registerInstancesWithLoadBalancer";
  public static final String DEREGISTER_INSTANCES_FROM_LOAD_BALANCER = "deregisterInstancesFromLoadBalancer";
  public static final String MAP_LOAD_BALANCERS = "mapLoadBalancers";
  public static final String UNMAP_LOAD_BALANCERS = "unmapLoadBalancers";
  public static final String ENABLE_INSTANCES_IN_DISCOVERY = "enableInstancesInDiscovery";
  public static final String DISABLE_INSTANCES_IN_DISCOVERY = "disableInstancesInDiscovery";
  public static final String UPDATE_INSTANCES = "updateInstances";
  public static final String DETACH_INSTANCES = "detachInstances";

  // Load Balancer operations
  public static final String DELETE_LOAD_BALANCER = "deleteLoadBalancer";
  public static final String UPSERT_LOAD_BALANCER = "upsertLoadBalancer";
  public static final String MIGRATE_LOAD_BALANCER = "migrateLoadBalancer";

  // Security Group operations
  public static final String DELETE_SECURITY_GROUP = "deleteSecurityGroup";
  public static final String UPSERT_SECURITY_GROUP = "upsertSecurityGroup";
  public static final String MIGRATE_SECURITY_GROUP = "migrateSecurityGroup";

  // JobStatus operations
  public static final String RUN_JOB = "runJob";
  public static final String DESTROY_JOB = "destroyJob";
  public static final String CLONE_JOB = "cloneJob";

  // Image operations
  public static final String UPSERT_IMAGE_TAGS = "upsertImageTags";

  // Snapshot operations
  public static final String SAVE_SNAPSHOT = "saveSnapshot";
  public static final String RESTORE_SNAPSHOT = "restoreSnapshot";

  // Manifest operations
  public static final String DEPLOY_MANIFEST = "deployManifest";
  public static final String DELETE_MANIFEST = "deleteManifest";
  public static final String SCALE_MANIFEST = "scaleManifest";
  public static final String PATCH_MANIFEST = "patchManifest";
  public static final String PAUSE_ROLLOUT_MANIFEST = "pauseRolloutManifest";
  public static final String RESUME_ROLLOUT_MANIFEST = "resumeRolloutManifest";
  public static final String UNDO_ROLLOUT_MANIFEST = "undoRolloutManifest";
  public static final String DISABLE_MANIFEST = "disableManifest";
  public static final String ENABLE_MANIFEST = "enableManifest";

  // Artifact operations
  public static final String CLEANUP_ARTIFACTS = "cleanupArtifacts";

  // Image operations
  public static final String DEREGISTER_IMAGE = "deleteImage";

  // Service operations
  public static final String DEPLOY_SERVICE = "deployService";
  public static final String DESTROY_SERVICE = "destroyService";
  public static final String SHARE_SERVICE = "shareService";
  public static final String UNSHARE_SERVICE = "unshareService";

  // CloudFormation operations
  public static final String DEPLOY_CLOUDFORMATION_STACK = "deployCloudFormation";
}
