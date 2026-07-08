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

package com.netflix.spinnaker.igor.config.auth;

import com.google.auth.oauth2.GoogleCredentials;
import com.netflix.spinnaker.igor.config.JenkinsProperties;
import okhttp3.Credentials;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class AuthRequestInterceptor implements Interceptor {
    private List<AuthorizationHeaderSupplier> suppliers = new ArrayList<>();

    public AuthRequestInterceptor(JenkinsProperties.JenkinsHost host) {
        // Order may be significant here.
        if (host.getUsername() != null && host.getPassword() != null) {
            suppliers.add(new BasicAuthHeaderSupplier(host.getUsername(), host.getPassword()));
        }
        if (host.getJsonPath() != null && host.getOauthScopes() != null) {
            suppliers.add(new GoogleBearerTokenHeaderSupplier(host.getJsonPath(), host.getOauthScopes()));
        } else if (host.getToken() != null) {
            suppliers.add(new BearerTokenHeaderSupplier(host.getToken()));
        }
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request.Builder builder = chain.request().newBuilder();
        if (!suppliers.isEmpty()) {
            String values = suppliers.stream().map(Object::toString).collect(Collectors.joining(", "));
            builder.addHeader("Authorization", values);
        }
      return chain.proceed(builder.build());
    }

    public interface AuthorizationHeaderSupplier {
        /**
         * Returns the value to be added as the value in the "Authorization" HTTP header.
         * @return
         */
        String toString();
    }

    public static class BasicAuthHeaderSupplier implements AuthorizationHeaderSupplier {

        private final String username;
        private final String password;

        public BasicAuthHeaderSupplier(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public String toString() {
            return Credentials.basic(username, password);
        }
    }

    public static class GoogleBearerTokenHeaderSupplier implements AuthorizationHeaderSupplier {

        private GoogleCredentials credentials;

        public GoogleBearerTokenHeaderSupplier(String jsonPath, List<String> scopes) {
            try (InputStream is = new FileInputStream(new File(jsonPath))) {
                credentials = GoogleCredentials.fromStream(is).createScoped(scopes);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load Google credentials", e);
            }
        }

        @Override
        public String toString() {
            log.debug("Including Google Bearer token in Authorization header");
            try {
                credentials.refreshIfExpired();
                return credentials.getAccessToken().getTokenValue();
            } catch (IOException e) {
                throw new RuntimeException("Failed to refresh Google credentials", e);
            }
        }
    }

    public static class BearerTokenHeaderSupplier implements AuthorizationHeaderSupplier {
        private String token;

        public BearerTokenHeaderSupplier(String token) {
            this.token = token;
        }

        @Override
        public String toString() {
            log.debug("Including raw bearer token in Authorization header");
            return "Bearer " + token;
        }
    }
}
