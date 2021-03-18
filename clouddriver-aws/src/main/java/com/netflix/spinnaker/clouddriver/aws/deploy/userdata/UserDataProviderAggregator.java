package com.netflix.spinnaker.clouddriver.aws.deploy.userdata;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataInput;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataOverride;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataProvider;
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataTokenizer;
import com.netflix.spinnaker.kork.exceptions.UserException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Aggregates all user data from the configured list of providers (see {@link UserDataProvider}).
 */
public class UserDataProviderAggregator {

  private final List<UserDataProvider> providers;
  private final List<UserDataTokenizer> tokenizers;

  public UserDataProviderAggregator(
      List<UserDataProvider> providers, List<UserDataTokenizer> tokenizers) {
    this.providers = providers;
    this.tokenizers = tokenizers;
  }

  /**
   * Aggregates all user data. First iterates through all providers and joins user data with a
   * newline. Then, adds the user supplied base64 encoded user data and again joins with a newline.
   * The result is such that the user supplied base64 encoded user data is always appended last to
   * the user data.
   *
   * <p>Note, if {@link UserDataOverride#isEnabled()} is true, then the user data from the providers
   * is skipped and the user supplied base64 encoded user data is used as the override. If this is
   * the case, user data format tokens (either a custom or default set) are replaced in the user
   * data - effectively processing the user data as a UDF template.
   *
   * @param userDataInput {@link UserDataInput}
   * @return String
   */
  public String aggregate(UserDataInput userDataInput) {
    byte[] bytes =
        Base64.getDecoder()
            .decode(Optional.ofNullable(userDataInput.getBase64UserData()).orElse(""));
    String userDataDecoded = new String(bytes, StandardCharsets.UTF_8);

    // If override default user data then we process tokens
    if (userDataInput.getUserDataOverride().isEnabled()) {
      List<UserDataTokenizer> udts =
          tokenizers.stream()
              .filter(it -> it.supports(userDataInput.getUserDataOverride().getTokenizerName()))
              .collect(Collectors.toList());

      if (udts.isEmpty()) {
        throw new UserException(
            "Unable to find supporting user data tokenizer for {}",
            userDataInput.getUserDataOverride().getTokenizerName());
      }

      for (UserDataTokenizer t : udts) {
        userDataDecoded =
            t.replaceTokens(
                Names.parseName(userDataInput.getAsgName()), userDataInput, userDataDecoded, false);
      }
      return result(Collections.singletonList(userDataDecoded));
    }

    List<String> allUserData = new ArrayList<>();
    if (providers != null && !userDataInput.getUserDataOverride().isEnabled()) {
      allUserData =
          providers.stream().map(p -> p.getUserData(userDataInput)).collect(Collectors.toList());
    }
    String data = String.join("\n", allUserData);

    return result(Arrays.asList(data, userDataDecoded));
  }

  private String result(List<String> parts) {
    String result = String.join("\n", parts);
    if (result.startsWith("\n")) {
      result = result.trim();
    }

    if (result.isEmpty()) {
      return null;
    }

    return Base64.getEncoder().encodeToString(result.getBytes(StandardCharsets.UTF_8));
  }
}
