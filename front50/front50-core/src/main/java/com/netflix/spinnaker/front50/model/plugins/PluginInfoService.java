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

import com.netflix.spinnaker.front50.config.annotations.ConditionalOnAnyProviderExceptRedisIsEnabled;
import com.netflix.spinnaker.front50.echo.EchoService;
import com.netflix.spinnaker.front50.plugins.PluginBinaryStorageService;
import com.netflix.spinnaker.front50.validator.GenericValidationErrors;
import com.netflix.spinnaker.front50.validator.PluginInfoValidator;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
@ConditionalOnAnyProviderExceptRedisIsEnabled
@Slf4j
public class PluginInfoService {

  private final PluginInfoRepository repository;
  private final Optional<PluginBinaryStorageService> storageService;
  private final Optional<EchoService> echoService;
  private final List<PluginInfoValidator> validators;

  public PluginInfoService(
      PluginInfoRepository repository,
      Optional<PluginBinaryStorageService> storageService,
      Optional<EchoService> echoService,
      List<PluginInfoValidator> validators) {
    this.repository = repository;
    this.storageService = storageService;
    this.echoService = echoService;
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

  /** Upserts the PluginInfo to the repository and sends Plugin Events to Echo */
  private PluginInfo savePluginInfo(PluginInfo pluginInfo) {
    validate(pluginInfo);

    PluginInfo oldPluginInfo;
    try {
      oldPluginInfo = findById(pluginInfo.getId());
    } catch (NotFoundException e) {
      oldPluginInfo = null;
    }

    if (oldPluginInfo == null) {
      repository.create(pluginInfo.getId(), pluginInfo);
    } else {
      repository.update(pluginInfo.getId(), pluginInfo);
    }

    PluginInfoDelta delta = new PluginInfoDelta(pluginInfo, oldPluginInfo);

    delta.addedReleases.forEach(
        release -> postEvent(PluginEventType.PUBLISHED, pluginInfo, release));

    String newVersion =
        Optional.ofNullable(delta.newPreferredRelease)
            .map(PluginInfo.Release::getVersion)
            .orElse(null);

    String oldVersion =
        Optional.ofNullable(delta.oldPreferredRelease)
            .map(PluginInfo.Release::getVersion)
            .orElse(null);

    if ((newVersion == null && oldVersion != null
        || (newVersion != null && !newVersion.equals(oldVersion)))) {
      postEvent(PluginEventType.PREFERRED_VERSION_UPDATED, pluginInfo, delta.newPreferredRelease);
    }

    return pluginInfo;
  }

  /** Upserts a *partial* PluginInfo. Validates that no Releases being upserted already existed */
  public PluginInfo upsert(@Nonnull PluginInfo pluginInfo) {
    try {
      PluginInfo currentPluginInfo = repository.findById(pluginInfo.getId());
      List<PluginInfo.Release> newReleases = new ArrayList<>(pluginInfo.getReleases());

      // upsert plugin info is not an authorized endpoint, so preferred release must be false.
      newReleases.forEach(it -> it.setPreferred(false));

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

      return savePluginInfo(pluginInfo);
    } catch (NotFoundException ignored) {
      return savePluginInfo(pluginInfo);
    }
  }

  public void delete(@Nonnull String id) {
    PluginInfo pluginInfo;
    try {
      pluginInfo = findById(id);
    } catch (NotFoundException e) {
      // Do nothing.
      return;
    }

    // Delete each release individually, so that the release binaries are also cleaned up.
    pluginInfo.getReleases().forEach(r -> deleteRelease(id, r.getVersion()));

    repository.delete(id);
  }

  public PluginInfo createRelease(@Nonnull String id, @Nonnull PluginInfo.Release release) {
    release.setLastModifiedBy(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    release.setLastModified(Instant.now());

    PluginInfo pluginInfo = repository.findById(id);
    pluginInfo.getReleases().add(release);

    if (release.isPreferred()) {
      preferRelease(pluginInfo, release);
    }

    return savePluginInfo(pluginInfo);
  }

  /**
   * Updates (not upserts) a release. Releases are effectively immutable, but this method can be
   * used to fixup data
   */
  public PluginInfo upsertRelease(@Nonnull String id, @Nonnull PluginInfo.Release release) {
    release.setLastModifiedBy(AuthenticatedRequest.getSpinnakerUser().orElse("anonymous"));
    release.setLastModified(Instant.now());
    PluginInfo pluginInfo = repository.findById(id);

    PluginInfo.Release existingRelease =
        pluginInfo
            .getReleaseByVersion(release.getVersion())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        String.format(
                            "Plugin %s with release %s version not found. ",
                            id, release.getVersion())));

    pluginInfo.getReleases().remove(existingRelease);
    pluginInfo.getReleases().add(release);

    if (release.isPreferred()) {
      preferRelease(pluginInfo, release);
    }

    return savePluginInfo(pluginInfo);
  }

  public PluginInfo deleteRelease(@Nonnull String id, @Nonnull String releaseVersion) {
    PluginInfo pluginInfo = repository.findById(id);

    Optional<PluginInfo.Release> release = pluginInfo.getReleaseByVersion(releaseVersion);
    release.ifPresent(it -> pluginInfo.getReleases().remove(it));

    savePluginInfo(pluginInfo);

    storageService.ifPresent(it -> it.delete(it.getKey(id, releaseVersion)));
    return pluginInfo;
  }

  /** Prefers the given release and sets all other releases to preferred = false; does not save. */
  private void preferRelease(@Nonnull PluginInfo pluginInfo, @Nullable PluginInfo.Release release) {
    String preferredVersion =
        Optional.ofNullable(release).map(PluginInfo.Release::getVersion).orElse(null);
    Instant now = Instant.now();
    String user = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");

    List<PluginInfo.Release> releases = new ArrayList<>(pluginInfo.getReleases());
    releases.forEach(
        it -> {
          boolean wasPreferredRelease = it.isPreferred();
          boolean isPreferredRelease = it.getVersion().equals(preferredVersion);

          if (isPreferredRelease != wasPreferredRelease) {
            it.setPreferred(isPreferredRelease);
            it.setLastModified(now);
            it.setLastModifiedBy(user);
          }
        });
  }

  /** Set the preferred release. If preferred is true, sets previous preferred release to false. */
  public PluginInfo.Release preferReleaseVersion(
      @Nonnull String id, @Nonnull String releaseVersion, boolean preferred) {
    PluginInfo pluginInfo = repository.findById(id);
    PluginInfo.Release release = pluginInfo.getReleaseByVersion(releaseVersion).orElse(null);

    // If preferred = false and the releaseVersion is currently preferred, unset all preferred flags
    if (!preferred && release != null && release.isPreferred()) {
      preferRelease(pluginInfo, null);
    } else if (preferred) {
      preferRelease(pluginInfo, release);
    }

    savePluginInfo(pluginInfo);

    return release;
  }

  private void validate(PluginInfo pluginInfo) {
    Errors errors = new GenericValidationErrors(pluginInfo);
    validators.forEach(v -> v.validate(pluginInfo, errors));
    if (errors.hasErrors()) {
      throw new ValidationException(errors);
    }
  }

  private void postEvent(PluginEventType type, PluginInfo pluginInfo, PluginInfo.Release release) {
    if (!echoService.isPresent()) {
      log.warn("Cannot send new plugin notification: Echo is not configured");
    } else if (release != null) {
      AuthenticatedRequest.allowAnonymous(
          () -> echoService.get().postEvent(new PluginEvent(type, pluginInfo, release)));
      log.debug("{} event posted", release);
    }
  }

  public static class ValidationException extends UserException {
    Errors errors;

    ValidationException(Errors errors) {
      this.errors = errors;
    }
  }
}
