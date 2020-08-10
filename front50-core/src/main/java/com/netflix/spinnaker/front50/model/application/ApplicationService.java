/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.front50.model.application;

import static com.netflix.spinnaker.front50.events.ApplicationEventListener.Type.*;

import com.google.common.base.Joiner;
import com.netflix.spinnaker.front50.ServiceAccountsService;
import com.netflix.spinnaker.front50.events.ApplicationEventListener;
import com.netflix.spinnaker.front50.events.ApplicationEventListener.ApplicationModelEvent;
import com.netflix.spinnaker.front50.exception.ValidationException;
import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel;
import com.netflix.spinnaker.front50.model.notification.NotificationDAO;
import com.netflix.spinnaker.front50.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO;
import com.netflix.spinnaker.front50.model.project.ProjectDAO;
import com.netflix.spinnaker.front50.validator.ApplicationValidationErrors;
import com.netflix.spinnaker.front50.validator.ApplicationValidator;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public class ApplicationService {

  private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
  private static final Joiner COMMA_JOINER = Joiner.on(',');

  private final ApplicationDAO dao;
  private final ProjectDAO projectDao;
  private final NotificationDAO notificationDao;
  private final PipelineDAO pipelineDao;
  private final PipelineStrategyDAO pipelineStrategyDao;
  private final Collection<ApplicationValidator> validators;
  private final Collection<ApplicationEventListener> applicationEventListeners;
  private final Optional<ServiceAccountsService> serviceAccountsService;

  public ApplicationService(
      ApplicationDAO dao,
      ProjectDAO projectDao,
      NotificationDAO notificationDao,
      PipelineDAO pipelineDao,
      PipelineStrategyDAO pipelineStrategyDao,
      Collection<ApplicationValidator> validators,
      Collection<ApplicationEventListener> applicationEventListeners,
      Optional<ServiceAccountsService> serviceAccountsService) {
    this.dao = dao;
    this.projectDao = projectDao;
    this.notificationDao = notificationDao;
    this.pipelineDao = pipelineDao;
    this.pipelineStrategyDao = pipelineStrategyDao;
    this.validators = validators;
    this.applicationEventListeners = applicationEventListeners;
    this.serviceAccountsService = serviceAccountsService;
  }

  public Application save(Application app) {
    return saveInternal(app, true);
  }

  public Application replace(Application app) {
    return saveInternal(app, false);
  }

  private Application saveInternal(Application app, boolean merge) {
    // When merge==true, the application that is passed in is likely incomplete, so the existing
    // application record must be loaded and applied to the partial Application. This must be
    // done prior to validation.
    Application existing = getApplication(app.getName());
    if (merge && existing != null) {
      app.setName(existing.getName());
      app.setCreateTs(existing.getCreateTs());
      if (app.getDescription() == null) {
        app.setDescription(existing.getDescription());
      }
      if (app.getEmail() == null) {
        app.setEmail(existing.getEmail());
      }
      if (app.getCloudProviders() == null) {
        app.setCloudProviders(existing.getCloudProviders());
      }

      mergeDetails(app, existing);
    }

    validate(app);

    if (existing == null) {
      listenersFor(PRE_CREATE)
          .forEach(it -> it.accept(new ApplicationModelEvent(PRE_CREATE, existing, app)));
    } else {
      listenersFor(PRE_UPDATE)
          .forEach(it -> it.accept(new ApplicationModelEvent(PRE_UPDATE, existing, app)));
    }

    dao.update(app.getName(), app);

    if (existing == null) {
      listenersFor(POST_CREATE)
          .forEach(it -> it.accept(new ApplicationModelEvent(POST_CREATE, existing, app)));
    } else {
      listenersFor(POST_UPDATE)
          .forEach(it -> it.accept(new ApplicationModelEvent(POST_UPDATE, existing, app)));
    }

    return app;
  }

  private void mergeDetails(Application app, Application existing) {
    if (existing == null) {
      return;
    }

    existing
        .details()
        .forEach(
            (key, value) -> {
              if (!app.details().containsKey(key)) {
                app.details().put(key, value);
              }
            });
  }

  public void delete(String appName) {
    Application application = getApplication(appName);
    if (application == null) {
      return;
    }

    listenersFor(PRE_DELETE)
        .forEach(it -> it.accept(new ApplicationModelEvent(PRE_DELETE, application, application)));

    // TODO(rz): Why does front50 sometimes want uppercase, and then other times lowercase?
    //  Make up your mind, fiddy.
    final String normalizedName = appName.toLowerCase();
    removeApplicationFromProjects(normalizedName);
    deleteApplicationFromNotifications(normalizedName);
    deletePipelines(normalizedName);

    dao.delete(application.getName());

    listenersFor(POST_DELETE)
        .forEach(it -> it.accept(new ApplicationModelEvent(POST_DELETE, application, application)));
  }

  private void removeApplicationFromProjects(String appName) {
    projectDao.all().stream()
        .filter(p -> p.getConfig().getApplications().contains(appName))
        .forEach(
            p -> {
              log.info("Removing application '{}' from project '{}'", appName, p.getId());
              p.getConfig().getApplications().remove(appName);
              Optional.ofNullable(p.getConfig().getClusters())
                  .ifPresent(
                      clusters ->
                          clusters.forEach(
                              c ->
                                  Optional.ofNullable(c.getApplications())
                                      .ifPresent(apps -> apps.remove((appName)))));

              // If the project doesn't have anymore applications, cascade the delete to projects.
              if (p.getConfig().getApplications().isEmpty()) {
                projectDao.delete(p.getId());
              } else {
                projectDao.update(p.getId(), p);
              }
            });
  }

  private void deleteApplicationFromNotifications(String appName) {
    notificationDao.delete(HierarchicalLevel.APPLICATION, appName);
  }

  private void deletePipelines(String appName) {
    // Delete pipelines
    Collection<Pipeline> pipelinesToDelete = pipelineDao.getPipelinesByApplication(appName);
    if (!pipelinesToDelete.isEmpty()) {
      List<String> pids =
          pipelinesToDelete.stream().map(Pipeline::getId).collect(Collectors.toList());
      log.info(
          "Deleting {} pipelines for application '{}': {}",
          pids.size(),
          appName,
          COMMA_JOINER.join(pids));
      pids.forEach(pipelineDao::delete);

      serviceAccountsService.ifPresent(
          svc -> {
            if (!pids.isEmpty()) {
              log.info(
                  "Deleting managed service accounts for application '{}': {}",
                  appName,
                  COMMA_JOINER.join(pids));
              svc.deleteManagedServiceAccounts(pids);
            }
          });
    }

    // Delete strategies
    Collection<Pipeline> strategiesToDelete =
        pipelineStrategyDao.getPipelinesByApplication(appName);
    if (!strategiesToDelete.isEmpty()) {
      List<String> sids =
          strategiesToDelete.stream().map(Pipeline::getId).collect(Collectors.toList());
      log.info(
          "Deleting {} strategies for application '{}': {}",
          sids.size(),
          appName,
          COMMA_JOINER.join(sids));
      sids.forEach(pipelineStrategyDao::delete);
    }
  }

  @Nullable
  public Application findByName(String name) {
    return getApplication(name);
  }

  @Nullable
  private Application getApplication(@Nullable String name) {
    try {
      return dao.findByName(Optional.ofNullable(name).map(String::toUpperCase).orElse(null));
    } catch (NotFoundException e) {
      // Exceptions for flow control == sad.
      return null;
    }
  }

  private List<ApplicationEventListener> listenersFor(ApplicationEventListener.Type type) {
    return applicationEventListeners.stream()
        .filter(it -> it.supports(type))
        .collect(Collectors.toList());
  }

  private void validate(Application application) {
    ApplicationValidationErrors errors = new ApplicationValidationErrors(application);
    validators.forEach(v -> v.validate(application, errors));
    if (errors.hasErrors()) {
      throw new ValidationException(errors);
    }
  }
}
