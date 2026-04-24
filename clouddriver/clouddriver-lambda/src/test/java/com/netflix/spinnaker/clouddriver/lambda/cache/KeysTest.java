package com.netflix.spinnaker.clouddriver.lambda.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class KeysTest {

  @Test
  void verifyBasicParsing() {

    assertThat(Keys.parse("aws:lambdaApplications:bob"))
        .containsEntry("application", "bob")
        .containsEntry("type", "lambdaApplications")
        .containsEntry("provider", "aws");
    assertThat(Keys.parse("aws:lambdaFunctions:my-account:my-region:my-lambda-name"))
        .containsEntry("AwsLambdaName", "my-lambda-name")
        .containsEntry("region", "my-region");
    // handle a bad key for some odd reason
    assertThat(Keys.parse("aws:lambdaApplications")).isEmpty();
  }
}
