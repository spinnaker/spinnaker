package com.netflix.spinnaker.orca.pipeline.util;

@Deprecated
public class OperatingSystem {
  private final PackageType packageType;

  public OperatingSystem(String os) {
    this.packageType = os.toLowerCase().matches("centos|rhel|redhat") ? PackageType.RPM : PackageType.DEB;
  }

  public PackageType getPackageType() {
    return packageType;
  }
}
