/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.echo.tasks;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.echo.EchoService;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class CreateJiraIssueTask implements RetryableTask {
  private final EchoService echoService;
  private final ObjectMapper mapper;

  @Autowired
  public CreateJiraIssueTask(EchoService echoService, ObjectMapper mapper) {
    this.echoService = echoService;
    this.mapper = mapper;
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(30);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(5);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    CreateIssueContext createIssueContext = stage.mapTo(CreateIssueContext.class);
    validateInputs(createIssueContext);

    EchoService.Notification notification = new EchoService.Notification();
    EchoService.Notification.Source source = new EchoService.Notification.Source();

    String user = getAuthenticatedUser(stage);
    if (user != null) {
      source.setUser(user);
      createIssueContext.getFields().setReporter(new CreateIssueContext.Fields.Reporter().withName(user));
      notification.setTo(Collections.singletonList(source.getUser()));
    }

    notification.setSource(source);
    notification.setNotificationType(EchoService.Notification.Type.JIRA);
    notification.setAdditionalContext(ImmutableMap.of("issueContext", createIssueContext));

    try {
      Response response = echoService.create(notification);
      return new TaskResult(
        ExecutionStatus.SUCCEEDED,
        ImmutableMap.of("createJiraIssueResponse", mapper.readValue(response.getBody().in(), Map.class))
      );

    } catch (Exception e) {
      throw new CreateJiraIssueException("Failed to create Jira Issue", e);
    }
  }

  private String getAuthenticatedUser(Stage stage) {
    return Optional.ofNullable(stage.getExecution().getAuthentication())
      .map(Execution.AuthenticationDetails::getUser).orElse(null);
  }

  private void validateInputs(CreateIssueContext createIssueContext) {
    Set<ConstraintViolation<CreateIssueContext>> violations = Validation.buildDefaultValidatorFactory()
      .getValidator()
      .validate(createIssueContext);
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(
        "Failed validation: " + violations.stream()
          .map(v -> String.format("%s: %s", v.getPropertyPath().toString(), v.getMessage()))
          .collect(Collectors.toList())
      );
    }
  }

  private static class HasDetails {
    Map<String, Object> details = new HashMap<>();

    @JsonAnySetter
    private void set(String name, Object value) {
      details.put(name, value);
    }

    @JsonAnyGetter
    private Map<String, Object> details() {
      return details;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  static class CreateIssueContext {
    private Fields fields;

    public Fields getFields() {
      return fields;
    }

    public void setFields(Fields fields) {
      this.fields = fields;
    }

    @Override
    public String toString() {
      return "CreateIssueContext{" +
        "fields=" + fields +
        '}';
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class Fields extends HasDetails {
      @NotNull private Project project;
      @NotNull private String description;
      @JsonProperty("issuetype") private IssueType issueType;
      private String summary;
      private Project parent;
      private Reporter reporter;
      private Map<String, Object> customFields;

      public Project getProject() {
        return project;
      }

      public void setProject(Project project) {
        this.project = project;
      }

      public String getSummary() {
        return summary;
      }

      public void setSummary(String summary) {
        this.summary = summary;
      }

      public String getDescription() {
        return description;
      }

      public void setDescription(String description) {
        this.description = description;
      }

      public IssueType getIssueType() {
        return issueType;
      }

      public void setIssueType(IssueType issueType) {
        this.issueType = issueType;
      }

      public Map<String, Object> getCustomFields() {
        return customFields;
      }

      public void setCustomFields(Map<String, Object> customFields) {
        this.customFields = customFields;
      }

      public Project getParent() {
        return parent;
      }

      public void setParent(Project parent) {
        this.parent = parent;
      }

      public Reporter getReporter() {
        return reporter;
      }

      public void setReporter(Reporter reporter) {
        this.reporter = reporter;
      }

      @Override
      public String toString() {
        return "Fields{" +
          "details=" + details +
          ", project=" + project +
          ", description='" + description + '\'' +
          ", issueType=" + issueType +
          ", summary='" + summary + '\'' +
          ", parent=" + parent +
          ", reporter='" + reporter + '\'' +
          ", customFields=" + customFields +
          '}';
      }

      static class Reporter {
        private String name;

        public Reporter withName(String name) {
          setName(name);
          return this;
        }

        public String getName() {
          return name;
        }

        public void setName(String name) {
          this.name = name;
        }

        @Override
        public String toString() {
          return "Reporter{" +
            "name='" + name + '\'' +
            '}';
        }
      }

      static class Project {
        private String id;

        public String getId() {
          return id;
        }

        public void setId(String id) {
          this.id = id;
        }

        @Override
        public String toString() {
          return "Project{" +
            "id='" + id + '\'' +
            '}';
        }
      }

      @JsonInclude(JsonInclude.Include.NON_NULL)
      static class IssueType {
        private String name;
        private String id;

        public String getName() {
          return name;
        }

        public void setName(String name) {
          this.name = name;
        }

        public String getId() {
          return id;
        }

        public void setId(String id) {
          this.id = id;
        }

        @Override
        public String toString() {
          return "IssueType{" +
            "name='" + name + '\'' +
            ", id='" + id + '\'' +
            '}';
        }
      }
    }
  }

  static class CreateJiraIssueException extends RuntimeException {
    public CreateJiraIssueException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
