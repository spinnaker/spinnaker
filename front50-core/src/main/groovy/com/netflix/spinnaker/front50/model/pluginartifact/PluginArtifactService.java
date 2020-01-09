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
package com.netflix.spinnaker.front50.model.pluginartifact;

import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.validator.GenericValidationErrors;
import com.netflix.spinnaker.front50.validator.PluginArtifactValidator;
import com.netflix.spinnaker.kork.exceptions.UserException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class PluginArtifactService {

  private final PluginArtifactRepository repository;
  private final List<PluginArtifactValidator> validators;

  public PluginArtifactService(
      PluginArtifactRepository repository, List<PluginArtifactValidator> validators) {
    this.repository = repository;
    this.validators = validators;
  }

  public Collection<PluginArtifact> findAll() {
    return repository.all();
  }

  public Collection<PluginArtifact> findAllByService(@Nonnull String service) {
    return repository.getByService(service);
  }

  public PluginArtifact findById(@Nonnull String pluginId) {
    return repository.findById(pluginId);
  }

  public PluginArtifact upsert(@Nonnull PluginArtifact pluginArtifact) {
    validate(pluginArtifact);

    try {
      repository.findById(pluginArtifact.getId());
      repository.update(pluginArtifact.getId(), pluginArtifact);
      return pluginArtifact;
    } catch (NotFoundException e) {
      return repository.create(pluginArtifact.getId(), pluginArtifact);
    }
  }

  public void delete(@Nonnull String id) {
    repository.delete(id);
  }

  public PluginArtifact createRelease(@Nonnull String id, @Nonnull PluginArtifact.Release release) {
    PluginArtifact artifact = repository.findById(id);

    artifact.getReleases().add(release);

    return upsert(artifact);
  }

  public PluginArtifact deleteRelease(@Nonnull String id, @Nonnull String releaseVersion) {
    PluginArtifact artifact = repository.findById(id);

    new ArrayList<>(artifact.getReleases())
        .forEach(
            release -> {
              if (release.getVersion().equals(releaseVersion)) {
                artifact.getReleases().remove(release);
              }
            });

    return upsert(artifact);
  }

  private void validate(PluginArtifact pluginArtifact) {
    Errors errors = new GenericValidationErrors(pluginArtifact);
    validators.forEach(v -> v.validate(pluginArtifact, errors));
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
