/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexClusterProvider;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.Collection;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Helper class for Yandex operations. Should be revised.
 *
 * <p>todo: fix credentials hierarchy, extract status utility methods, replace autowiring with
 * contructors
 *
 * @param <D>
 */
public class AbstractYandexAtomicOperation<D extends CredentialsNameable> {
  protected final D description;
  protected final YandexCloudCredentials credentials;

  @Autowired protected YandexCloudFacade yandexCloudFacade;
  @Autowired private YandexClusterProvider yandexClusterProvider;

  AbstractYandexAtomicOperation(D description) {
    this.description = description;
    this.credentials = (YandexCloudCredentials) description.getCredentials();
  }

  protected Optional<YandexCloudServerGroup> getServerGroup(String name) {
    return Optional.ofNullable(
        yandexClusterProvider.getServerGroup(
            description.getAccount(), YandexCloudProvider.REGION, name));
  }

  protected YandexCloudServerGroup getServerGroup(String phase, String name) {
    return getServerGroup(name)
        .orElseThrow(
            () -> new IllegalStateException(status(phase, "Not found server group '%s'!", name)));
  }

  public static String status(String phase, String status, Object... args) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(phase, String.format(status, args));
    return status;
  }

  public static <T> Optional<T> single(Collection<T> values) {
    return values.size() == 1 ? Optional.of(values.iterator().next()) : Optional.empty();
  }
}
