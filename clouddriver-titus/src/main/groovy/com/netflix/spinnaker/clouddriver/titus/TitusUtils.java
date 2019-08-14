/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.titus;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import com.netflix.spinnaker.clouddriver.titus.exceptions.UnexpectedAccountCredentialsTypeException;
import javax.annotation.Nonnull;

/** A collection of utility methods for Titus. */
public class TitusUtils {

  /** Get the AWS Account ID for a particular Titus or AWS account. */
  @Nonnull
  public static String getAccountId(
      @Nonnull AccountCredentialsProvider accountCredentialsProvider, @Nonnull String credentials) {
    AccountCredentials accountCredentials = accountCredentialsProvider.getCredentials(credentials);
    if (accountCredentials instanceof NetflixTitusCredentials) {
      return accountCredentialsProvider
          .getCredentials(((NetflixTitusCredentials) accountCredentials).getAwsAccount())
          .getAccountId();
    }
    return accountCredentials.getAccountId();
  }

  /** Assert that the provided AccountCredentials is a NetflixTitusCredentials type. */
  public static void assertTitusAccountCredentialsType(AccountCredentials accountCredentials) {
    if (!(accountCredentials instanceof NetflixTitusCredentials)) {
      throw new UnexpectedAccountCredentialsTypeException(
          format(
              "Account credentials for '%s' was expected to be NetflixTitusCredentials, but got '%s'",
              accountCredentials.getName(), accountCredentials.getClass().getSimpleName()),
          format(
              "There may be a configuration error for Titus: '%s' account was requested, but it is not a Titus account",
              accountCredentials.getName()));
    }
  }
}
