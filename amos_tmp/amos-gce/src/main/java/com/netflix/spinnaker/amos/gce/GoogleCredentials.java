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

package com.netflix.spinnaker.amos.gce;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.compute.Compute;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GoogleCredentials {
    private final String project;
    private final Compute compute;

    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final String jsonKey;

    public GoogleCredentials(String project, Compute compute, HttpTransport httpTransport, JsonFactory jsonFactory, String jsonKey) {
        this.project = project;
        this.compute = compute;
        this.httpTransport = httpTransport;
        this.jsonFactory = jsonFactory;
        this.jsonKey = jsonKey;
    }

    public String getProject() {
        return project;
    }

    public Compute getCompute() {
        return compute;
    }

    public GoogleCredential.Builder createCredentialBuilder(String... serviceAccountScopes) {
        final Set<String> accountScopes = new HashSet<>(Arrays.asList(serviceAccountScopes));
        final GoogleCredential.Builder builder = new GoogleCredential.Builder() {
            @Override
            public GoogleCredential build() {
                try {
                    GoogleCredential credential = jsonKey != null
                            ? GoogleCredential.fromStream(new ByteArrayInputStream(jsonKey.getBytes()))
                            : GoogleCredential.getApplicationDefault();
                    return credential.createScoped(accountScopes);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            };
        };

        return builder.setTransport(httpTransport).setJsonFactory(jsonFactory);
    }

}
