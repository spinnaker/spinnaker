package com.netflix.spinnaker.clouddriver.aws.deploy.userdata;

import com.google.common.base.Strings;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataInput;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataTokenizer;

public class DefaultUserDataTokenizer implements UserDataTokenizer {

  @Override
  public String replaceTokens(
      Names names, UserDataInput userDataInput, String rawUserData, Boolean legacyUdf) {
    String stack = isPresent(names.getStack()) ? names.getStack() : "";
    String cluster = isPresent(names.getCluster()) ? names.getCluster() : "";
    String revision = isPresent(names.getRevision()) ? names.getRevision() : "";
    String countries = isPresent(names.getCountries()) ? names.getCountries() : "";
    String devPhase = isPresent(names.getDevPhase()) ? names.getDevPhase() : "";
    String hardware = isPresent(names.getHardware()) ? names.getHardware() : "";
    String zone = isPresent(names.getZone()) ? names.getZone() : "";
    String detail = isPresent(names.getDetail()) ? names.getDetail() : "";

    // Replace the tokens & return the result
    String result =
        rawUserData
            .replace("%%account%%", userDataInput.getAccount())
            .replace("%%accounttype%%", userDataInput.getAccountType())
            .replace(
                "%%env%%",
                (legacyUdf != null && legacyUdf)
                    ? userDataInput.getAccount()
                    : userDataInput.getEnvironment())
            .replace("%%app%%", names.getApp())
            .replace("%%region%%", userDataInput.getRegion())
            .replace("%%group%%", names.getGroup())
            .replace("%%autogrp%%", names.getGroup())
            .replace("%%revision%%", revision)
            .replace("%%countries%%", countries)
            .replace("%%devPhase%%", devPhase)
            .replace("%%hardware%%", hardware)
            .replace("%%zone%%", zone)
            .replace("%%cluster%%", cluster)
            .replace("%%stack%%", stack)
            .replace("%%detail%%", detail)
            .replace("%%tier%%", "");

    if (userDataInput.getLaunchTemplate() != null && userDataInput.getLaunchTemplate()) {
      result =
          result
              .replace("%%launchtemplate%%", userDataInput.getLaunchSettingName())
              .replace("%%launchconfig%%", "");
    } else {
      result =
          result
              .replace("%%launchconfig%%", userDataInput.getLaunchSettingName())
              .replace("%%launchtemplate%%", "");
    }

    return result;
  }

  private static boolean isPresent(String value) {
    return !Strings.isNullOrEmpty(value);
  }
}
