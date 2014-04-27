package com.netflix.kato.deploy.aws.userdata

public interface UserDataProvider {
  String getUserData(String asgName, String launchConfigName, String region)
}