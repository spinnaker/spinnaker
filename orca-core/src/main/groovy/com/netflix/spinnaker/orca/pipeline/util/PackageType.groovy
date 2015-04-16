package com.netflix.spinnaker.orca.pipeline.util

public enum PackageType {
  RPM('rpm', '-'),
  DEB('deb', '_')

  private final String packageType
  private final String versionDelimiter

  private PackageType(String packageType, String versionDelimiter) {
    this.packageType = packageType
    this.versionDelimiter = versionDelimiter
  }

  String getPackageType() {
    return this.packageType
  }

  String getVersionDelimiter() {
    return this.versionDelimiter
  }
}
