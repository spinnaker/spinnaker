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

import groovy.transform.CompileStatic
import org.hibernate.validator.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties

import javax.validation.Valid

/**
 * Helper class to map masters in properties file into a validated property map
 */
@CompileStatic
@ConfigurationProperties(prefix = 'jenkins')
class JenkinsProperties {
    @Valid
    List<JenkinsHost> masters

    static class JenkinsHost {
        @NotEmpty
        String name

        @NotEmpty
        String address

        @NotEmpty
        String username

        @NotEmpty
        String password
    }
}
