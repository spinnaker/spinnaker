/*
 * Copyright 2026 Harness, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;

/**
 * v3 feature flag resource object.
 *
 * @see <a href="https://v3-apidocs.cloudfoundry.org/index.html#the-feature-flag-object">CAPI v3
 *     Feature Flag</a>
 */
@Data
public class FeatureFlag {
  private String name;
  private boolean enabled;
  @Nullable private String updatedAt;
  @Nullable private String customErrorMessage;
  @Nullable private Map<String, Link> links;
}
