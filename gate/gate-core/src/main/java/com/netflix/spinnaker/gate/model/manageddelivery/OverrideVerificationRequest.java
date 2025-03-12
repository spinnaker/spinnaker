/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.gate.model.manageddelivery;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.NonNull;

@Data
public class OverrideVerificationRequest {
  @NonNull @Nonnull String verificationId;
  @NonNull @Nonnull String artifactReference;
  @NonNull @Nonnull String artifactVersion;
  @NonNull @Nonnull String status;
  @Nullable String comment;
}
