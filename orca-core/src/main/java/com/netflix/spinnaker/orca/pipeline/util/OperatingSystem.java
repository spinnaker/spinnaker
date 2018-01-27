package com.netflix.spinnaker.orca.pipeline.util;

@Deprecated public enum OperatingSystem {
  centos(PackageType.RPM),
  ubuntu(PackageType.DEB),
  trusty(PackageType.DEB),
  xenial(PackageType.DEB);

  OperatingSystem(PackageType packageType) {
    this.packageType = packageType;
  }

  public PackageType getPackageType() {
    return packageType;
  }

  private final PackageType packageType;
}
