/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.clouddriver.event.EventMetadata;
import com.netflix.spinnaker.clouddriver.saga.SagaEvent;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;

@Builder(builderClassName = "TitusScalingPolicyModifiedBuilder", toBuilder = true)
@JsonDeserialize(builder = TitusScalingPolicyModified.TitusScalingPolicyModifiedBuilder.class)
@JsonTypeName("titusScalingPolicyModified")
@Value
public class TitusScalingPolicyModified implements SagaEvent {
  @Nonnull private final String account;

  @Nonnull private final String region;

  @Nonnull private final String jobId;

  @Nonnull private final String scalingPolicyId;

  @NonFinal private EventMetadata metadata;

  @Override
  public void setMetadata(@NotNull EventMetadata metadata) {
    this.metadata = metadata;
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class TitusScalingPolicyModifiedBuilder {}
}
