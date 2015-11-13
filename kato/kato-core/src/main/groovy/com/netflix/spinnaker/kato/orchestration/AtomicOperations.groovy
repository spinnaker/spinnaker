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

package com.netflix.spinnaker.kato.orchestration

/**
 * A class that holds the names of ALL the kato operations as constants
 *
 */
final class AtomicOperations {

  // Server Group operations
  public static final String CLONE_SERVER_GROUP = "cloneServerGroup"
  public static final String CREATE_SERVER_GROUP = "createServerGroup"
  public static final String DISABLE_SERVER_GROUP = "disableServerGroup"
  public static final String ENABLE_SERVER_GROUP = "enableServerGroup"
  public static final String DESTROY_SERVER_GROUP = "destroyServerGroup"
  public static final String RESIZE_SERVER_GROUP = "resizeServerGroup"

  // Instance operations
  public static final String REBOOT_INSTANCES = "rebootInstances"
  public static final String TERMINATE_INSTANCES = "terminateInstances"
  public static final String TERMINATE_INSTANCE_AND_DECREMENT = "terminateInstanceAndDecrementServerGroup"
  public static final String ATTACH_CLASSIC_LINK_VPC = "attachClassicLinkVpc"

  // Load Balancer operations
  public static final String DELETE_LOAD_BALANCER = "deleteLoadBalancer"
  public static final String UPSERT_LOAD_BALANCER = "upsertLoadBalancer"

  // Security Group operations
  public static final String DELETE_SECURITY_GROUP = "deleteSecurityGroup"
  public static final String UPSERT_SECURITY_GROUP = "upsertSecurityGroup"
}
