/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.roles.google

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.admin.directory.Directory
import com.google.api.services.admin.directory.DirectoryScopes
import com.google.api.services.admin.directory.model.Group
import com.google.api.services.admin.directory.model.Groups
import com.netflix.spinnaker.fiat.roles.UserRolesProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.util.Assert

@Slf4j
@Component
@ConditionalOnProperty(value = "auth.groupMembership.service", havingValue = "google")
class GoogleDirectoryUserRolesProvider implements UserRolesProvider, InitializingBean {

  @Autowired
  Config config

  private static final Collection<String> SERVICE_ACCOUNT_SCOPES = [DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY]

  @Override
  void afterPropertiesSet() throws Exception {
    Assert.state(config.domain != null, "Supply a domain")
    Assert.state(config.adminUsername != null, "Supply an admin username")
  }

  private class GroupBatchCallback extends JsonBatchCallback<Groups> {

    Map<String, Collection<String>> emailGroupsMap

    String email

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.error("Failed to fetch groups: " + e.getMessage())
    }

    @Override
    void onSuccess(Groups groups, HttpHeaders responseHeaders) throws IOException {
      emailGroupsMap[email] = groups.getGroups().collect { Group g -> g.getName() }
    }
  }

  @Override
  Map<String, Collection<String>> multiLoadRoles(Collection<String> userEmails) {
    if (!userEmails) {
      return [:]
    }
    def emailGroupsMap = [:]
    Directory service = getDirectoryService()
    BatchRequest batch = service.batch()
    userEmails.each { String email ->
      service
          .groups()
          .list()
          .setDomain(config.domain)
          .setUserKey(email)
          .queue(batch, new GroupBatchCallback(emailGroupsMap: emailGroupsMap, email: email))
    }
    batch.execute()
    emailGroupsMap
  }

  @Override
  List<String> loadRoles(String userEmail) {
    if (!userEmail) {
      return []
    }
    Directory service = getDirectoryService()
    Groups groups = service.groups().list().setDomain(config.domain).setUserKey(userEmail).execute()
    return groups.getGroups().collect { Group g -> g.getName() }
  }

  private GoogleCredential getGoogleCredential() {
    if (config.credentialPath) {
      return GoogleCredential.fromStream(new FileInputStream(config.credentialPath))
    } else {
      return GoogleCredential.applicationDefault
    }
  }

  Directory getDirectoryService() {
    HttpTransport httpTransport = new NetHttpTransport()
    JacksonFactory jacksonFactory = new JacksonFactory()
    GoogleCredential credential = getGoogleCredential()
    credential.with {
      serviceAccountUser = config.adminUsername
      serviceAccountScopes = SERVICE_ACCOUNT_SCOPES
    }

    return new Directory.Builder(httpTransport, jacksonFactory, credential)
        .setApplicationName("Spinnaker-Gate")
        .build()
  }

  @Configuration
  @ConfigurationProperties("auth.groupMembership.google")
  static class Config {

    /**
     * Path to json credential file for the groups service account.
     */
    String credentialPath

    /**
     * Email of the Google Apps admin the service account is acting on behalf of.
     */
    String adminUsername

    /**
     * Google Apps for Work domain, e.g. netflix.com
     */
    String domain
  }
}
