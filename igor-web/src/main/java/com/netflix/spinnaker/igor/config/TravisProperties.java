/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

import javax.validation.Valid;
import java.util.List;

@ConfigurationProperties(prefix = "travis")
public class TravisProperties {
    private boolean repositorySyncEnabled = false;
    private int cachedJobTTLDays = 60;
    @Valid
    private List<TravisHost> masters;
    @Valid
    private List<String> regexes;

    public boolean getRepositorySyncEnabled() {
        return repositorySyncEnabled;
    }

    public boolean isRepositorySyncEnabled() {
        return repositorySyncEnabled;
    }

    public void setRepositorySyncEnabled(boolean repositorySyncEnabled) {
        this.repositorySyncEnabled = repositorySyncEnabled;
    }

    public int getCachedJobTTLDays() {
        return cachedJobTTLDays;
    }

    public void setCachedJobTTLDays(int cachedJobTTLDays) {
        this.cachedJobTTLDays = cachedJobTTLDays;
    }

    public List<TravisHost> getMasters() {
        return masters;
    }

    public void setMasters(List<TravisHost> masters) {
        this.masters = masters;
    }

    public List<String> getRegexes() {
        return regexes;
    }

    public void setRegexes(List<String> regexes) {
        this.regexes = regexes;
    }

    public static class TravisHost {
        @NotEmpty
        private String name;
        @NotEmpty
        private String baseUrl;
        @NotEmpty
        private String address;
        @NotEmpty
        private String githubToken;
        private int numberOfRepositories = 25;
        private Integer itemUpperThreshold;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getGithubToken() {
            return githubToken;
        }

        public void setGithubToken(String githubToken) {
            this.githubToken = githubToken;
        }

        public int getNumberOfRepositories() {
            return numberOfRepositories;
        }

        public void setNumberOfRepositories(int numberOfRepositories) {
            this.numberOfRepositories = numberOfRepositories;
        }

        public Integer getItemUpperThreshold() {
            return itemUpperThreshold;
        }

        public void setItemUpperThreshold(Integer itemUpperThreshold) {
            this.itemUpperThreshold = itemUpperThreshold;
        }

    }
}
