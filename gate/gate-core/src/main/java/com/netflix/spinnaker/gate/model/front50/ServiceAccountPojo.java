/*
 * Copyright 2026 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.model.front50;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A simple POJO representing a service account with just the name field. This is a subset of
 * front50's ServiceAccount class (which implements Timestamped) and is used to deserialize
 * responses from front50's /serviceAccounts endpoint when only the name is needed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAccountPojo {
  private String name;
}
