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

package com.netflix.spinnaker.clouddriver.google.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.auth.oauth2.TokenRequest
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.GenericUrl
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.iam.v1.Iam
import com.google.api.services.iam.v1.model.SignJwtRequest
import com.google.common.base.Joiner

import java.security.GeneralSecurityException

class GoogleImpersonatedCredential extends GoogleCredential {

  String serviceAccountId
  String serviceAccountProject

  ObjectMapper mapper = new ObjectMapper()

  GoogleImpersonatedCredential(GoogleCredential.Builder builder, String serviceAccountId, String serviceAccountProject) {
    super(builder)
    // These fields can't be in the builder because of some null checking that prohibits the ability
    // to do this impersonation.
    this.serviceAccountId = serviceAccountId
    this.serviceAccountProject = serviceAccountProject
  }

  /**
   * This modifies the behavior of the GoogleCredential refresh to allow the ability for the
   * instance's service account (aka application default credentials) to impersonate another service
   * account by using the signJwt endpoint. The instance's service account must have the
   * 'Service Account Token Creator' role.
   *
   * This use case differs slightly from the 'GoogleCredential.Builder().setServiceAccountUser.'
   * With that setter method, the credential still requires the private key of the service account.
   * Attempts to circumvent that requirement (by using reflection magic to set the field) resulted
   * in a 401 Unauthorized response from the server.
   *
   * This only applies when running Clouddriver on GCP (where Application Default Credentials are
   * available). You can emulate this environment by setting `GOOGLE_APPLICATION_CREDENTIALS` to
   * a location of a private key. It's not exactly the same, but works pretty similar. See
   * https://developers.google.com/identity/protocols/application-default-credentials for more info.
   *
   * @return
   * @throws IOException
   */
  @Override
  protected TokenResponse executeRefreshToken() throws IOException {
    def scopes = Collections.singleton(ComputeScopes.CLOUD_PLATFORM)
    long currentTime = getClock().currentTimeMillis()

    def payload = new JwtPayload(
        iss: serviceAccountId,
        aud: getTokenServerEncodedUrl(),
        scope: Joiner.on(", ").join(scopes),
        iat: currentTime / 1000,
        ext: currentTime / 1000 + 3600
    )

    SignJwtRequest req = new SignJwtRequest().setPayload(mapper.writeValueAsString(payload))
    String fullAccountId = "projects/${serviceAccountProject}/serviceAccounts/${serviceAccountId}"

    try {
      Iam iam = new Iam.Builder(getTransport(),
                                jsonFactory,
                                getApplicationDefault().createScoped(scopes)).build()
      def signedJwt = iam.projects().serviceAccounts().signJwt(fullAccountId, req).execute()

      TokenRequest request = new TokenRequest(
          getTransport(), getJsonFactory(), new GenericUrl(getTokenServerEncodedUrl()),
          "urn:ietf:params:oauth:grant-type:jwt-bearer")
      request.put("assertion", signedJwt.getSignedJwt())
      return request.execute()
    } catch (GeneralSecurityException exception) {
      IOException e = new IOException()
      e.initCause(exception)
      throw e
    }
  }

  class JwtPayload {
    String iss
    String scope
    String aud
    Long iat
    Long ext
  }
}
