package com.netflix.spinnaker.orca.libdiffs;

/**
 * Implementation of this interface provides a way to compare two "loose" library versions
 */
public interface ComparableLooseVersion {
  /**
   * Returns if 0, -1 or 1 if the {@code lhsVersion} is same, before or after {@code rhsVersion} respectively
   *
   * @param lhsVersion
   * @param rhsVersion
   * @return
   */
  int compare(String lhsVersion, String rhsVersion);
}
