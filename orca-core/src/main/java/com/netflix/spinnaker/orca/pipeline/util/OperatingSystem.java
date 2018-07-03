package com.netflix.spinnaker.orca.pipeline.util;

import org.springframework.util.StringUtils;

@Deprecated
public class OperatingSystem {
  private final PackageType packageType;

  public OperatingSystem(String os) {
    this.packageType = (StringUtils.hasLength(os) && os.toLowerCase().matches("centos|rhel|redhat")) ?
      PackageType.RPM : PackageType.DEB;
  }

  public PackageType getPackageType() {
    return packageType;
  }
}
