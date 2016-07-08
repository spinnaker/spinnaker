/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import groovy.transform.CompileStatic

@CompileStatic
class TestCredential {
    public static NetflixAmazonCredentials named(String name, Map params = [:]) {
        def credJson = [
                name: name,
                environment: name,
                accountType: name,
                accountId: "123456789012" + name,
                defaultKeyPair: 'default-keypair',
                regions: [[name: 'us-east-1', availabilityZones: ['us-east-1b', 'us-east-1c', 'us-east-1d']],
                          [name: 'us-west-1', availabilityZones: ["us-west-1a", "us-west-1b"]]],
        ] + params

        new ObjectMapper().convertValue(credJson, NetflixAmazonCredentials)
    }
}
