package com.netflix.spinnaker.clouddriver.aws.userdata;

/**
 * Implementations of this interface will provide user data to instances during the deployment
 * process.
 */
public interface UserDataProvider {

  /**
   * Provide user data from the specified request.
   *
   * @param userDataInput {@link UserDataInput}
   * @return String
   */
  default String getUserData(UserDataInput userDataInput) {
    return "";
  }
}
