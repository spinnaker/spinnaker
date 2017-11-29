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

package com.netflix.spinnaker.igor.config.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.squareup.okhttp.Credentials
import groovy.util.logging.Slf4j
import retrofit.RequestInterceptor

@Slf4j
class AuthRequestInterceptor implements RequestInterceptor {
    List<AuthorizationHeaderSupplier> suppliers = []

    AuthRequestInterceptor(JenkinsProperties.JenkinsHost host) {
        // Order may be significant here.
        if (host.username && host.password) {
            suppliers.add(new BasicAuthHeaderSupplier(host.username, host.password))
        }
        if (host.jsonPath && host.oauthScopes) {
            suppliers.add(new GoogleBearerTokenHeaderSupplier(host.jsonPath, host.oauthScopes))
        } else if (host.token) {
            suppliers.add(new BearerTokenHeaderSupplier(token: host.token))
        }
    }

    @Override
    void intercept(RequestInterceptor.RequestFacade request) {
        if (suppliers) {
            def values = suppliers.join(", ")
            request.addHeader("Authorization", values)
        }
    }

    static interface AuthorizationHeaderSupplier {
        /**
         * Returns the value to be added as the value in the "Authorization" HTTP header.
         * @return
         */
        String toString()
    }

    static class BasicAuthHeaderSupplier implements AuthorizationHeaderSupplier {

        private final String username
        private final String password

        BasicAuthHeaderSupplier(String username, String password) {
            this.username = username
            this.password = password
        }

        String toString() {
            log.debug("Including Basic digest in Authorization header")
            return Credentials.basic(username, password)
        }
    }

    static class GoogleBearerTokenHeaderSupplier implements AuthorizationHeaderSupplier {

        private GoogleCredential credential

        GoogleBearerTokenHeaderSupplier(String jsonPath, List<String> scopes) {
            InputStream is = new File(jsonPath).newInputStream()
            credential = GoogleCredential.fromStream(is).createScoped(scopes)
        }

        String toString() {
            log.debug("Including Google Bearer token in Authorization header")
            if (credential.expirationTimeMilliseconds < System.currentTimeMillis()) {
                log.info("Google OAuth Access token expired. Refreshing.")
                credential.refreshToken()
            }
            return credential.accessToken
        }
    }

    static class BearerTokenHeaderSupplier implements AuthorizationHeaderSupplier {
        private token

        String toString() {
            log.debug("Including raw bearer token in Authorization header")
            return "Bearer ${token}".toString()
        }
    }
}
