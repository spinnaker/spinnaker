/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.providers.appengine;

public class AppengineCommandProperties {
  static final String LOCAL_REPOSITORY_DIRECTORY_DESCRIPTION =
      "A local directory to be used to stage source files"
          + " for App Engine deployments within Spinnaker's Clouddriver microservice.";
  static final String GIT_HTTPS_USERNAME_DESCRIPTION =
      "A username to be used when connecting with a remote git" + " repository server over HTTPS.";
  static final String GIT_HTTPS_PASSWORD_DESCRIPTION =
      "A password to be used when connecting with a remote git" + " repository server over HTTPS.";
  static final String GITHUB_OAUTH_ACCESS_TOKEN_DESCRIPTION =
      "An OAuth token provided by Github for connecting to "
          + " a git repository over HTTPS."
          + " See https://help.github.com/articles/creating-an-access-token-for-command-line-use for more information.";
  static final String SSH_PRIVATE_KEY_FILE_PATH =
      "The path to an SSH private key to be used when"
          + " connecting with a remote git repository over SSH.";
  static final String SSH_PRIVATE_KEY_PASSPHRASE =
      "The passphrase to an SSH private key to be used"
          + " when connecting with a remote git repository over SSH.";
  static final String SSH_KNOWN_HOSTS_FILE_PATH =
      "The path to a known_hosts file to be used when connecting with"
          + " a remote git repository over SSH.";
  static final String SSH_TRUST_UNKNOWN_HOSTS =
      "Enabling this flag will allow Spinnaker to connect"
          + " with a remote git repository over SSH without verifying the server's IP address"
          + " against a known_hosts file.";
  static final String GCLOUD_RELEASE_TRACK =
      "The gcloud release track (ALPHA, BETA, or STABLE) that Spinnaker"
          + " will use when deploying to App Engine.";
  static final String SERVICES =
      "A list of regular expressions. Any service matching one of these regexes "
          + "will be indexed by Spinnaker.";
  static final String VERSIONS =
      "A list of regular expressions. Any version matching one of these regexes "
          + "will be indexed by Spinnaker.";
  static final String OMIT_SERVICES =
      "A list of regular expressions. Any service matching one of these regexes "
          + "will be ignored by Spinnaker.";
  static final String OMIT_VERSIONS =
      "A list of regular expressions. Any version matching one of these regexes "
          + "will be ignored by Spinnaker.";
  static final String CACHING_INTERVAL_SECONDS =
      "The interval in seconds at which Spinnaker will poll for updates "
          + "in your AppEngine clusters.";
}
