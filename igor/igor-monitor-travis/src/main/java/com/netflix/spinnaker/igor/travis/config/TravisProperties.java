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

package com.netflix.spinnaker.igor.travis.config;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.config.BuildServerProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travis")
@Data
@Slf4j
public class TravisProperties implements BuildServerProperties<TravisProperties.TravisHost> {
  @Deprecated private long newBuildGracePeriodSeconds;
  private boolean repositorySyncEnabled = false;
  private int cachedJobTTLDays = 60;
  @Valid private List<TravisHost> masters;
  @Valid private List<String> regexes;
  /**
   * Lets you customize the build message used when Spinnaker triggers builds in Travis. If you set
   * a custom parameter in the Travis stage in Spinnaker with the value of this property as the key
   * (e.g <code>travis.buildMessage=My customized message</code>, the build message in Travis will
   * be <em>Triggered from Spinnaker: My customized message</em>. The first part of this message is
   * not customizable.
   */
  private String buildMessageKey = "travis.buildMessage";

  @Deprecated
  public void setNewBuildGracePeriodSeconds(long newBuildGracePeriodSeconds) {
    log.warn(
        "The 'travis.newBuildGracePeriodSeconds' property is no longer in use and the value will be ignored.");
    this.newBuildGracePeriodSeconds = newBuildGracePeriodSeconds;
  }

  @Data
  public static class TravisHost implements BuildServerProperties.Host {
    @NotEmpty private String name;
    @NotEmpty private String baseUrl;
    @NotEmpty private String address;
    @NotEmpty private String githubToken;
    @Deprecated private int numberOfRepositories;
    /** Defines how many jobs Igor should retrieve per polling cycle. Defaults to 100. */
    private int numberOfJobs = 100;
    /**
     * Defines how many builds Igor should return when querying for builds for a specific repo. This
     * affects for instance how many builds that will be displayed in the drop down when starting a
     * manual execution of a pipeline. If set too high, the Travis API might return an error for
     * jobs that writes a lot of logs, which is why the default setting is a bit conservative.
     */
    private int buildResultLimit = 10;

    private Collection<String> filteredRepositories = Collections.emptySet();

    private Integer itemUpperThreshold;
    private Permissions.Builder permissions = new Permissions.Builder();
    /**
     * The Travis Builds and Jobs API supports an attribute called <code>log_complete</code> that is
     * supposed to tell us if the log is ready to be downloaded. Igor has been using this attribute
     * to cut down on the number of potentially expensive API calls needed towards Travis during
     * polling. However, relying on <code>log_complete</code> has been unreliable for some Travis
     * users, so we're disabling it by default.
     */
    private boolean useLogComplete = false;

    @Deprecated
    public void setNumberOfRepositories(int numberOfRepositories) {
      log.warn(
          "The 'travis.numberOfRepositories' property is no longer in use and the value will be ignored. "
              + "If you want to limit the number of builds retrieved per polling cycle, you can use the property "
              + "'travis.[master].numberOfJobs' (default: 100).");
      this.numberOfRepositories = numberOfRepositories;
    }
  }
}
