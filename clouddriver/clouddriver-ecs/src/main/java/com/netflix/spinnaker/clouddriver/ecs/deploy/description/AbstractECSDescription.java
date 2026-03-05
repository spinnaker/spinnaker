/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.description;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription;
import com.netflix.spinnaker.moniker.Moniker;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public abstract class AbstractECSDescription extends AbstractAmazonCredentialsDescription {
  /**
   * @deprecated This field is deprecated in favour of [moniker.app]
   */
  @Deprecated String application;

  /**
   * @deprecated This field is deprecated in favour of [moniker.stack]
   */
  @Deprecated String stack;

  /**
   * @deprecated This field is deprecated in favour of [moniker.detail]
   */
  @Deprecated String freeFormDetails;

  String region;
  Moniker moniker;
}
