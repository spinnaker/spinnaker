/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.errors.v1;

import lombok.Data;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

/**
 * This is what Halyard will return to a client in case of an error fixable by the client. This includes validation
 * failures, lack of a halconfig file, a bad deployment, etc...
 */
@Data
public class HalconfigFixableIssue {
  /**
   * A set of messages for things that Halyard is positive is a problem. This includes verifiably incorrect credentials,
   * lists of regions/namespaces not supported by the cloud provider, unresolved references to other configuration, etc...
   */
  protected List<String> errors = new ArrayList<>();
  /**
   * Warnings are things that Halyard thinks might be a problem. This includes credentials with leading/trailing spaces,
   * an apparent but uncheckable lack of IAM permissions, etc...
   */
  protected List<String> warnings = new ArrayList<>();
}
