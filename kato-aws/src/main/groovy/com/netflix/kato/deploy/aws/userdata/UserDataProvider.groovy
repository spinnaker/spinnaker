package com.netflix.kato.deploy.aws.userdata

/**
 * Implementations of this interface will provide user data to instances during the deployment process
 *
 * @author Dan Woods
 */
public interface UserDataProvider {
  /**
   * Returns user data that will be applied to a new instance. The launch configuration will not have been created at
   * this point in the workflow, but the name is provided, as it may be needed when building user data detail.
   *
   * @param asgName
   * @param launchConfigName
   * @param region
   *
   * @return user data string
   */
  String getUserData(String asgName, String launchConfigName, String region, String environment)
}