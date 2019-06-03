/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.halyard.config.model.v1.providers.oracle;

import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@ToString
@EqualsAndHashCode(callSuper = true)
public class OracleBaseImage
    extends BaseImage<
        OracleBaseImage.OracleImageSettings, OracleBaseImage.OracleVirtualizationSettings> {

  private OracleImageSettings baseImage;
  private OracleVirtualizationSettings virtualizationSettings;

  @EqualsAndHashCode(callSuper = true)
  @Data
  @ToString(callSuper = true)
  public static class OracleImageSettings extends BaseImage.ImageSettings {
    // We have none to set
  }

  @Data
  @ToString
  public static class OracleVirtualizationSettings {
    String baseImageId;
    String sshUserName;
  }
}
