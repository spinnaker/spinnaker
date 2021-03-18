/*
 * Copyright 2019 Netflix, Inc.
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
import com.netflix.spinnaker.clouddriver.titus.JobType;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder(builderClassName = "TitusJobSubmittedBuilder", toBuilder = true)
@JsonDeserialize(builder = TitusJobSubmitted.TitusJobSubmittedBuilder.class)
@JsonTypeName("titusJobSubmitted")
@Value
public class TitusJobSubmitted implements SagaEvent {

  @Nonnull private final Map<String, String> serverGroupNameByRegion;
  @Nonnull private final String jobUri;
  @Nonnull private final JobType jobType;
  @NonFinal private EventMetadata metadata;

  @Override
  public void setMetadata(EventMetadata metadata) {
    this.metadata = metadata;
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class TitusJobSubmittedBuilder {}
}
