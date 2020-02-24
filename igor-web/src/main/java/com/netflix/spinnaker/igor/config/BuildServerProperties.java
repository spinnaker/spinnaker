/*
 * Copyright 2019 Schibsted ASA.
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

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.List;

/**
 * Interface for representing the properties of a Build Service (CI) provider in Spinnaker. An
 * example configuration file could look like this:
 *
 * <pre>{@code
 * someProvider:
 *   masters:
 *   - name: someProvider-host1
 *     address: https://foo.com/api
 *     permissions:
 *       READ:
 *       - foo
 *       - bar
 *       WRITE:
 *       - bar
 *   - name: someProvider-host2
 *     address: https://bar.com/api
 * }</pre>
 *
 * @param <T> The implementation type of each host
 */
public interface BuildServerProperties<T extends BuildServerProperties.Host> {

  /**
   * Returns a list of the build service hosts configured with this provider
   *
   * @return The build service hosts
   */
  List<T> getMasters();

  /** Interface for representing the properties of a specific build service host */
  interface Host {
    /**
     * Get the name of the build service host
     *
     * @return The name of the build service host
     */
    String getName();

    /**
     * Get the address of the build service host
     *
     * @return The address of the build service host
     */
    String getAddress();

    /**
     * Get the permissions needed to access this build service host. Read permissions are needed to
     * trigger Spinnaker pipelines on successful builds on the build service host (if set, users
     * without the correct permissions won't see the host in Spinnaker), while write permissions are
     * needed to trigger jobs/builds on the build service host from Spinnaker. Can be left blank; If
     * so, the host will not be protected. An example configuration should look like this:
     *
     * <pre>{@code
     * someProvider:
     *   masters:
     *   - name: someProvider-host1
     *     address: https://foo.com/api
     *     permissions:
     *       READ:
     *       - foo
     *       - bar
     *       WRITE:
     *       - bar
     *   - name: someProvider-host2
     *     address: https://bar.com/api
     * }</pre>
     *
     * In the example above, users with the foo or bar roles will be able to see <code>
     * someProvider-host1</code> and use it as a trigger, users with the bar role will additionally
     * be able to trigger builds on the CI host. Users without these roles will not see <code>
     * someProvider-host1</code> in Spinnaker at all. All users will be able to access <code>
     * someProvider-host2</code> without limitations.
     *
     * @return The permissions needed to access this build service host
     */
    Permissions.Builder getPermissions();
  }
}
