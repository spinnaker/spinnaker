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

package com.netflix.spinnaker.orca.echo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JiraService {
  private final EchoService echoService;
  private final ObjectMapper mapper;

  @Autowired
  public JiraService(EchoService echoService, ObjectMapper mapper) {
    this.echoService = echoService;
    this.mapper = mapper;
  }

  public CreateJiraIssueResponse createJiraIssue(CreateIssueRequest createIssueRequest) throws CreateJiraIssueException {
    try {
      validateInputs(createIssueRequest);
      EchoService.Notification notification = new EchoService.Notification();
      EchoService.Notification.Source source = new EchoService.Notification.Source();
      Optional.ofNullable(createIssueRequest.getFields().getReporter()).ifPresent(reporter -> {
        source.setUser(reporter.getName());
        notification.setTo(Collections.singletonList(reporter.getName()));
      });

      notification.setSource(source);
      notification.setNotificationType(EchoService.Notification.Type.JIRA);
      notification.setAdditionalContext(ImmutableMap.of("issueContext", createIssueRequest));
      Response response = echoService.create(notification);
      return mapper.readValue(response.getBody().in(), CreateJiraIssueResponse.class);
    } catch (Exception e) {
      throw new CreateJiraIssueException(
        String.format("Failed to create Jira Issue for project %s %s", createIssueRequest.getFields().getProject(), e.getMessage())
      );
    }
  }

  private void validateInputs(CreateIssueRequest createIssueRequest) {
    Set<ConstraintViolation<CreateIssueRequest>> violations = Validation.buildDefaultValidatorFactory()
      .getValidator()
      .validate(createIssueRequest);
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(
        "Failed validation: " + violations.stream()
          .map(v -> String.format("%s: %s", v.getPropertyPath().toString(), v.getMessage()))
          .collect(Collectors.toList())
      );
    }
  }

  static class CreateJiraIssueException extends RuntimeException {
    public CreateJiraIssueException(String message) {
      super(message);
    }
  }

  public static class CreateJiraIssueResponse {
    private String id;
    private String key;
    private String self;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getSelf() {
      return self;
    }

    public void setSelf(String self) {
      this.self = self;
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
  public static class CreateIssueRequest {
    private Fields fields;

    public Fields getFields() {
      return fields;
    }

    public void setFields(Fields fields) {
      this.fields = fields;
    }

    public void setReporter(String reporter) {
      new JiraService.CreateIssueRequest.Fields.Reporter().withName(reporter);
    }

    @Override
    public String toString() {
      return "CreateIssueContext{" +
        "fields=" + fields +
        '}';
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Fields extends HasDetails {
      @NotNull
      private Project project;
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

      public static class Reporter {
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

      @JsonInclude(JsonInclude.Include.NON_NULL)
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
}
