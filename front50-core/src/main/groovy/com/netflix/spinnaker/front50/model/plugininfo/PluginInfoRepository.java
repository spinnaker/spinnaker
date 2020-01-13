/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.front50.model.plugininfo;

import com.netflix.spinnaker.front50.model.ItemDAO;
import com.netflix.spinnaker.front50.model.plugininfo.PluginInfo.Release;
import java.util.Collection;
import javax.annotation.Nonnull;

public interface PluginInfoRepository extends ItemDAO<PluginInfo> {
  /**
   * Returns a collection of plugins that should be installed by a particular service.
   *
   * <p>This is determined by inference, using a {@link Release}'s {@code requires} field.
   */
  @Nonnull
  Collection<PluginInfo> getByService(@Nonnull String service);
}
