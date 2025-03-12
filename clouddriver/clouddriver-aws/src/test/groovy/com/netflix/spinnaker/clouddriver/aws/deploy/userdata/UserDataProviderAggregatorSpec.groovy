package com.netflix.spinnaker.clouddriver.aws.deploy.userdata

import com.amazonaws.services.ec2.model.UserData
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataInput
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataProvider
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataTokenizer
import org.apache.commons.io.IOUtils
import spock.lang.Specification
import spock.lang.Unroll

class UserDataProviderAggregatorSpec extends Specification {

  UserDataProviderAggregator userDataProviderAggregator = new UserDataProviderAggregator([new UserDataProviderA(), new UserDataProviderB()], [new DefaultUserDataTokenizer(), new CustomTokenizer()])

  static final String APP = 'app'
  static final String STACK = 'stack'
  static final String COUNTRIES = 'countries'
  static final String DEV_PHASE = 'devPhase'
  static final String HARDWARE = 'hardware'
  static final String PARTNERS = 'partners'
  static final String REVISION = 99
  static final String ZONE = 'zone'
  static final String REGION = 'region'
  static final String ACCOUNT = 'account'
  static final String ENVIRONMENT = 'environment'
  static final String ACCOUNT_TYPE = 'accountType'
  static final String DETAIL = "detail-c0${COUNTRIES}-d0${DEV_PHASE}-h0${HARDWARE}-p0${PARTNERS}-r0${REVISION}-z0${ZONE}"
  static final String ASG_NAME = "${APP}-${STACK}-${DETAIL}"
  static final String LAUNCH_CONFIG_NAME = 'launchConfigName'

  void "User data is aggregated correctly; a -> b -> user supplied user data"() {
    given:
    UserDataInput request = UserDataInput
      .builder()
      .asgName(ASG_NAME)
      .launchSettingName(LAUNCH_CONFIG_NAME)
      .environment(ENVIRONMENT)
      .region(REGION)
      .account(ACCOUNT)
      .accountType(ACCOUNT_TYPE)
      .userDataOverride(new UserDataOverride())
      .base64UserData("ZXhwb3J0IFVTRVJEQVRBPTEK")
      .build()

    when:
    //export USERDATA=1
    String result = userDataProviderAggregator.aggregate(request)

    then:
    //a
    //b
    //export USERDATA=1
    result == "YQpiCmV4cG9ydCBVU0VSREFUQT0xCg=="
  }

  @Unroll
  void "User data is overrode with the user supplied base64 encoded user data and tokens are replaced correctly - #userDataFileName"() {
    given:
    String tokenizedUserdata = IOUtils.toString(getClass().getResourceAsStream("${userDataFileName}-tokenized.txt"))
    String expectedResult = Base64.getEncoder().encodeToString(tokenizedUserdata.getBytes("utf-8"))

    String userdata = IOUtils.toString(getClass().getResourceAsStream("${userDataFileName}.txt"))
    String base64String = Base64.getEncoder().encodeToString(userdata.getBytes("utf-8"))

    UserDataInput request = UserDataInput
      .builder()
      .asgName(ASG_NAME)
      .launchSettingName(LAUNCH_CONFIG_NAME)
      .environment(ENVIRONMENT)
      .region(REGION)
      .account(ACCOUNT)
      .accountType(ACCOUNT_TYPE)
      .userDataOverride(userDataOverride)
      .base64UserData(base64String)
      .build()

    when:
    String result = userDataProviderAggregator.aggregate(request)

    then:
    result == expectedResult

    where:
    userDataOverride                                             | userDataFileName
    new UserDataOverride(enabled: true)                          | "default-token-userdata"
    new UserDataOverride(enabled: true, tokenizerName: "custom") | "custom-token-userdata"
  }
}

class UserDataProviderA implements UserDataProvider {
  String getUserData(UserDataInput userDataRequest) {
    return "a"
  }
}

class UserDataProviderB implements UserDataProvider {
  String getUserData(UserDataInput userDataRequest) {
    return "b"
  }
}

class CustomTokenizer implements UserDataTokenizer {

  @Override
  boolean supports(String tokenizerName) {
    return tokenizerName == "custom"
  }

  @Override
  String replaceTokens(Names names, UserDataInput userDataInput, String rawUserData, Boolean legacyUdf) {
    return rawUserData
        .replace("%%custom_token_a%%", "custom-a")
        .replace("%%custom_token_b%%", "custom-b")
  }
}
