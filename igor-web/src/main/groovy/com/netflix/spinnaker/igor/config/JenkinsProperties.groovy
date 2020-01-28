/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.config

import com.netflix.spinnaker.fiat.model.resources.Permissions
import groovy.transform.CompileStatic
import org.hibernate.validator.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

import javax.validation.Valid
import java.security.KeyStore

/**
 * Helper class to map masters in properties file into a validated property map
 */
@CompileStatic
@ConfigurationProperties(prefix = 'jenkins')
@Validated
class JenkinsProperties implements BuildServerProperties<JenkinsProperties.JenkinsHost> {
    @Valid
    List<JenkinsHost> masters

    static class JenkinsHost implements BuildServerProperties.Host {
        @NotEmpty
        String name

        @NotEmpty
        String address

        String username

        String password

        Boolean csrf = false

        // These are needed for Google-based OAuth with a service account credential
        String jsonPath
        List<String> oauthScopes = []

        // Can be used directly, if available.
        String token

        Integer itemUpperThreshold;

        String trustStore
        String trustStoreType = KeyStore.getDefaultType()
        String trustStorePassword

        String keyStore
        String keyStoreType = KeyStore.getDefaultType()
        String keyStorePassword

        Boolean skipHostnameVerification = false

        Permissions.Builder permissions = new Permissions.Builder()
    }
}
