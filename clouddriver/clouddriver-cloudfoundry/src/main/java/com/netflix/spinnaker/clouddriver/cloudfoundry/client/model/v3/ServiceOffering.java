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

import lombok.Data;

/**
 * v3 {@code /v3/service_offerings} resource, replacing the v2 {@code Service} model. Note that v3
 * promotes "shareable" to a first-class top-level field, rather than something buried in an opaque
 * {@code extra} JSON blob as it was on the v2 {@code Service} entity.
 */
@Data
public class ServiceOffering {
  private String guid;
  private String name;
  private boolean shareable;
}
