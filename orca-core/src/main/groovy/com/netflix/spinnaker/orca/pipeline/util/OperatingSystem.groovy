package com.netflix.spinnaker.orca.pipeline.util

public enum OperatingSystem {
  centos(PackageType.RPM), ubuntu(PackageType.DEB), trusty(PackageType.DEB)

  private final PackageType packageType
  private OperatingSystem(PackageType packageType) {
    this.packageType = packageType
  }

  PackageType getPackageType() {
    return packageType
  }
}

