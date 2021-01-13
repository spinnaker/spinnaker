package com.netflix.spinnaker.clouddriver.aws.userdata;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;

/** Utility interface to replace tokens in user data templates. */
public interface UserDataTokenizer extends SpinnakerExtensionPoint {

  /**
   * If this instance supports the specified tokenizer.
   *
   * @param tokenizerName - the tokenizer the instance supports. The default tokenizer is "default"
   *     and is found first if multiple "default" supporting user data tokenizers are found.
   * @return boolean
   */
  default boolean supports(String tokenizerName) {
    return tokenizerName.equals("default");
  }

  /**
   * Replaces the tokens that are present in the supplied user data.
   *
   * @param names {@link Names}
   * @param userDataInput {@link UserDataInput}
   * @param rawUserData The user data to replace tokens in
   * @param legacyUdf
   * @return String
   */
  String replaceTokens(
      Names names, UserDataInput userDataInput, String rawUserData, Boolean legacyUdf);
}
