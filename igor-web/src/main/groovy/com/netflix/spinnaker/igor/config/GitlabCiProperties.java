/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.igor.config;

import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.List;

@ConfigurationProperties(prefix = "gitlab-ci")
@Validated
public class GitlabCiProperties {
    private int cachedJobTTLDays = 60;

    @Valid
    private List<GitlabCiHost> masters;


    public int getCachedJobTTLDays() {
        return cachedJobTTLDays;
    }

    public void setCachedJobTTLDays(int cachedJobTTLDays) {
        this.cachedJobTTLDays = cachedJobTTLDays;
    }

    public List<GitlabCiHost> getMasters() {
        return masters;
    }

    public void setMasters(List<GitlabCiHost> masters) {
        this.masters = masters;
    }

    public static class GitlabCiHost {
        @NotEmpty
        private String name;
        @NotEmpty
        private String address;
        private String privateToken;
        private boolean limitByMembership = false;
        private boolean limitByOwnership = true;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getPrivateToken() {
            return privateToken;
        }

        public void setPrivateToken(String privateToken) {
            this.privateToken = privateToken;
        }

        public boolean getLimitByMembership() {
            return limitByMembership;
        }

        public boolean isLimitByMembership() {
            return limitByMembership;
        }

        public void setLimitByMembership(boolean limitByMembership) {
            this.limitByMembership = limitByMembership;
        }

        public boolean getLimitByOwnership() {
            return limitByOwnership;
        }

        public boolean isLimitByOwnership() {
            return limitByOwnership;
        }

        public void setLimitByOwnership(boolean limitByOwnership) {
            this.limitByOwnership = limitByOwnership;
        }
    }
}
