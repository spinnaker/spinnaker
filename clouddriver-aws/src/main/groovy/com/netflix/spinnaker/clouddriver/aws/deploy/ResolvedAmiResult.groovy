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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.services.ec2.model.BlockDeviceMapping
import groovy.transform.Immutable

/**
 * The result of looking up amiName in a region to find amiId
 */
@Immutable
class ResolvedAmiResult {
  String amiName
  String region
  String amiId
  String virtualizationType
  String ownerId
  List<BlockDeviceMapping> blockDeviceMappings
  Boolean isPublic
}
