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

import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.validator.GenericValidationErrors;
import com.netflix.spinnaker.front50.validator.PluginInfoValidator;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class PluginInfoService {

  private final PluginInfoRepository repository;
  private final List<PluginInfoValidator> validators;

  public PluginInfoService(PluginInfoRepository repository, List<PluginInfoValidator> validators) {
    this.repository = repository;
    this.validators = validators;
  }

  public Collection<PluginInfo> findAll() {
    return repository.all();
  }

  public Collection<PluginInfo> findAllByService(@Nonnull String service) {
    return repository.getByService(service);
  }

  public PluginInfo findById(@Nonnull String pluginId) {
    return repository.findById(pluginId);
  }

  public PluginInfo upsert(@Nonnull PluginInfo pluginInfo) {
    validate(pluginInfo);

    try {
      PluginInfo currentPluginInfo = repository.findById(pluginInfo.getId());
      List<PluginInfo.Release> newReleases = new ArrayList<>(pluginInfo.getReleases());
      List<PluginInfo.Release> oldReleases = new ArrayList<>(currentPluginInfo.getReleases());
      newReleases.forEach(
          release -> { // Raise an exception if old releases are being updated.
            if (oldReleases.stream()
                .anyMatch(oldRelease -> oldRelease.getVersion().equals(release.getVersion()))) {
              throw new InvalidRequestException(
                  "Cannot update an existing release: " + release.getVersion());
            }
          });

      List<PluginInfo.Release> allReleases = new ArrayList<>();
      Stream.of(oldReleases, newReleases).forEach(allReleases::addAll);
      pluginInfo.setReleases(allReleases);
      repository.update(pluginInfo.getId(), pluginInfo);
      return pluginInfo;
    } catch (NotFoundException e) {
      return repository.create(pluginInfo.getId(), pluginInfo);
    }
  }

  public void delete(@Nonnull String id) {
    repository.delete(id);
  }

  public PluginInfo createRelease(@Nonnull String id, @Nonnull PluginInfo.Release release) {
    PluginInfo pluginInfo = repository.findById(id);
    pluginInfo.getReleases().add(release);
    validate(pluginInfo);
    repository.update(pluginInfo.getId(), pluginInfo);
    return pluginInfo;
  }

  public PluginInfo deleteRelease(@Nonnull String id, @Nonnull String releaseVersion) {
    PluginInfo pluginInfo = repository.findById(id);

    new ArrayList<>(pluginInfo.getReleases())
        .forEach(
            release -> {
              if (release.getVersion().equals(releaseVersion)) {
                pluginInfo.getReleases().remove(release);
              }
            });
    repository.update(pluginInfo.getId(), pluginInfo);
    return pluginInfo;
  }

  private void validate(PluginInfo pluginInfo) {
    Errors errors = new GenericValidationErrors(pluginInfo);
    validators.forEach(v -> v.validate(pluginInfo, errors));
    if (errors.hasErrors()) {
      throw new ValidationException(errors);
    }
  }

  public static class ValidationException extends UserException {
    Errors errors;

    ValidationException(Errors errors) {
      this.errors = errors;
    }
  }
}
