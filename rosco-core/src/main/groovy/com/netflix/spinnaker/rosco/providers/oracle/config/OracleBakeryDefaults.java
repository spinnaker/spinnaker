/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.rosco.providers.oracle.config;

import java.util.List;

public class OracleBakeryDefaults {
  private String availabilityDomain;
  private String subnetId;
  private String instanceShape;

  private String templateFile;

  private List<OracleOperatingSystemVirtualizationSettings> baseImages;

  public String getAvailabilityDomain() {
    return availabilityDomain;
  }

  public void setAvailabilityDomain(String availabilityDomain) {
    this.availabilityDomain = availabilityDomain;
  }

  public String getSubnetId() {
    return subnetId;
  }

  public void setSubnetId(String subnetId) {
    this.subnetId = subnetId;
  }

  public String getInstanceShape() {
    return instanceShape;
  }

  public void setInstanceShape(String instanceShape) {
    this.instanceShape = instanceShape;
  }

  public String getTemplateFile() {
    return templateFile;
  }

  public void setTemplateFile(String templateFile) {
    this.templateFile = templateFile;
  }

  public List<OracleOperatingSystemVirtualizationSettings> getBaseImages() {
    return baseImages;
  }

  public void setBaseImages(List<OracleOperatingSystemVirtualizationSettings> baseImages) {
    this.baseImages = baseImages;
  }
}
