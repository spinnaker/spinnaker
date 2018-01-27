package com.netflix.spinnaker.orca.pipeline.util;

public enum PackageType {
  RPM("rpm", "-"),
  DEB("deb", "_"),
  NUPKG("nupkg", ".");

  PackageType(String packageType, String versionDelimiter) {
    this.packageType = packageType;
    this.versionDelimiter = versionDelimiter;
  }

  public String getPackageType() {
    return this.packageType;
  }

  public String getVersionDelimiter() {
    return this.versionDelimiter;
  }

  private final String packageType;
  private final String versionDelimiter;
}
