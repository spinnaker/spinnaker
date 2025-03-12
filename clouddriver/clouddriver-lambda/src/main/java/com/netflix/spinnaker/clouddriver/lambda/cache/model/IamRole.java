/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.cache.model;

import com.netflix.spinnaker.clouddriver.aws.model.Role;
import com.netflix.spinnaker.clouddriver.aws.model.TrustRelationship;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IamRole implements Role {

  /*
  The ID is the AWS ARN, in the format arn:aws:iam::account-id:role/role-name
   */
  String id;

  String name;
  String accountName;
  Set<? extends TrustRelationship> trustRelationships;
}
