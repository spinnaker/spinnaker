/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.providers.google;

import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;

@Data
public class GoogleBaseImage extends BaseImage<GoogleBaseImage.GoogleImageSettings, GoogleBaseImage.GoogleVirtualizationSettings> {
  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  private GoogleImageSettings baseImage;
  private GoogleVirtualizationSettings virtualizationSettings;

  @Data
  public static class GoogleImageSettings extends BaseImage.ImageSettings {
    boolean isImageFamily;
  }

  @Data
  public static class GoogleVirtualizationSettings {
    String sourceImageFamily;
  }
}
