/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.ops.loadbalancer

class CreateGoogleHttpLoadBalancerTestConstants {
  static final LOAD_BALANCER_NAME = "spinnaker-test-v000"
  static final ACCOUNT_NAME = "auto"
  static final CHECK_INTERVAL_SEC = 7
  static final INSTANCE_GROUP = "group"
  static final IP_ADDRESS = "1.1.1.1"
  static final PORT_RANGE = "80-82"
  static final BALANCING_MODE = "super_balanced"
  static final SERVICE = "service"
  static final PATH = "/path"
  static final HOST = "*"
  static final MATCHER = "matcher"
}
