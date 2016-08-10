/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.image;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ImageTagger {
  String OPERATION = "upsertImageTags";

  /**
   * @return the OperationContext object that contains the cloud provider-specific list of operations as well as
   * cloud provider-specific output key/value pairs to be included in the task's output.
   */
  OperationContext getOperationContext(Stage stage);

  /**
   * @return true when, according to the underlying cloud provider, the machine image tags have been updated to match the
   * target machine image.
   */
  boolean isImageTagged(Image targetImage, Stage stage);

  /**
   * @return The cloud provider type that this object supports.
   */
  String getCloudProvider();

  class OperationContext {
    final List<Map<String, Map>> operations;
    final Map<String, Object> extraOutput;

    public OperationContext(List<Map<String, Map>> operations, Map<String, Object> extraOutput) {
      this.operations = operations;
      this.extraOutput = extraOutput;
    }
  }

  class Image {
    public final String imageName;
    public final String account;
    public final Collection<String> regions;
    public final Map<String, String> tags;

    @JsonCreator
    public Image(@JsonProperty("imageName") String imageName,
                 @JsonProperty("account") String account,
                 @JsonProperty("regions") Collection<String> regions,
                 @JsonProperty("tags") Map<String, String> tags) {
      this.imageName = imageName;
      this.account = account;
      this.regions = regions;
      this.tags = tags;
    }
  }

  class ImageNotFound extends RuntimeException {
    final boolean shouldRetry;

    public ImageNotFound(String message, boolean shouldRetry) {
      super(message);
      this.shouldRetry = shouldRetry;
    }
  }
}
