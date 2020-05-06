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
package com.netflix.spinnaker.front50.model.plugins;

import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.validator.GenericValidationErrors;
import com.netflix.spinnaker.front50.validator.PluginInfoValidator;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
      // Because we first validate the plugin info, we can assume there will be only one preferred
      // release
      Optional<PluginInfo.Release> preferredRelease =
          pluginInfo.getReleases().stream().filter(PluginInfo.Release::isPreferred).findFirst();

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
      preferredRelease.ifPresent(release -> cleanupPreferredReleases(pluginInfo, release));

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
    release.setLastModifiedBy(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    release.setLastModified(Instant.now());

    PluginInfo pluginInfo = repository.findById(id);
    pluginInfo.getReleases().add(release);
    cleanupPreferredReleases(pluginInfo, release);

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

  /** Set the preferred release. If preferred is true, sets previous preferred release to false. */
  public PluginInfo.Release preferReleaseVersion(
      @Nonnull String id, @Nonnull String releaseVersion, boolean preferred) {
    PluginInfo pluginInfo = repository.findById(id);
    Optional<PluginInfo.Release> release = pluginInfo.getReleaseByVersion(releaseVersion);

    Instant now = Instant.now();
    String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");

    return release
        .map(
            r -> {
              r.setPreferred(preferred);
              r.setLastModified(now);
              r.setLastModifiedBy(user);

              pluginInfo.setReleaseByVersion(releaseVersion, r);
              cleanupPreferredReleases(pluginInfo, r);

              repository.update(pluginInfo.getId(), pluginInfo);
              return r;
            })
        .orElse(null);
  }

  private void validate(PluginInfo pluginInfo) {
    Errors errors = new GenericValidationErrors(pluginInfo);
    validators.forEach(v -> v.validate(pluginInfo, errors));
    if (errors.hasErrors()) {
      throw new ValidationException(errors);
    }
  }

  private void cleanupPreferredReleases(PluginInfo pluginInfo, PluginInfo.Release release) {
    if (release.isPreferred()) {
      Instant now = Instant.now();
      String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");

      pluginInfo.getReleases().stream()
          .filter(it -> !it.getVersion().equals(release.getVersion()))
          .forEach(
              it -> {
                it.setPreferred(false);
                it.setLastModified(now);
                it.setLastModifiedBy(user);
              });
    }
  }

  public static class ValidationException extends UserException {
    Errors errors;

    ValidationException(Errors errors) {
      this.errors = errors;
    }
  }
}
