/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kork.aws.bastion;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.netflix.spinnaker.kork.aws.bastion.RemoteCredentialsSupport.RemoteCredentials;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BastionCredentialsProvider implements AWSCredentialsProvider {
  private static final String CREDENTIALS_BASE_URL =
      "http://169.254.169.254/latest/meta-data/iam/security-credentials";
  private static final Logger log = LoggerFactory.getLogger(BastionCredentialsProvider.class);

  private final String user;
  private final String host;
  private final Integer port;
  private final String proxyCluster;
  private final String proxyRegion;
  private final String iamRole;

  private Date expiration;
  private AWSCredentials credentials;
  private final SimpleDateFormat format =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

  public BastionCredentialsProvider(
      String user,
      String host,
      Integer port,
      String proxyCluster,
      String proxyRegion,
      String iamRole) {
    this.user = user == null ? (String) System.getProperties().get("user.name") : user;
    this.host = host;
    this.port = port;
    this.proxyCluster = proxyCluster;
    this.proxyRegion = proxyRegion;
    this.iamRole = iamRole;
  }

  @Override
  public AWSCredentials getCredentials() {
    if (expiration == null || expiration.before(new Date())) {
      this.credentials = getRemoteCredentials();
    }
    return this.credentials;
  }

  @Override
  public void refresh() {
    this.credentials = getRemoteCredentials();
  }

  private AWSCredentials getRemoteCredentials() {
    final String command =
        String.format(
            "oq-ssh -r %s %s,0 'curl -s %s/%s'",
            proxyRegion, proxyCluster, CREDENTIALS_BASE_URL, iamRole);
    final RemoteCredentials credentials =
        RemoteCredentialsSupport.getRemoteCredentials(command, user, host, port);

    try {
      expiration = format.parse(credentials.getExpiration());
    } catch (ParseException e) {
      log.error("Failed to parse credentials expiration {}", credentials.getExpiration(), e);
      throw new IllegalStateException(e);
    }

    return new BasicSessionCredentials(
        credentials.getAccessKeyId(), credentials.getSecretAccessKey(), credentials.getToken());
  }
}
