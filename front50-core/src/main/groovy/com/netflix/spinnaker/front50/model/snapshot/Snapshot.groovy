/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.front50.model.snapshot

import com.netflix.spinnaker.front50.model.Timestamped

/*
 * A snapshot contains a description of the server groups, load balancers, autoscalers
 * and security groups in the scope of the account and application. These resources are stored in a string
 * using a config language such as Terraform, CloudFormation or Heat.
 */

class Snapshot implements Timestamped {
  enum Type {
    TERRAFORM,
  }

  String id
  String application
  String account

  // Describes the resources deployed in the cloud of the application and account
  Map infrastructure

  // Resources are described using this config language, used for restoring the snapshot
  Type configLang

  Long lastModified
  String lastModifiedBy
  Long timestamp
}
