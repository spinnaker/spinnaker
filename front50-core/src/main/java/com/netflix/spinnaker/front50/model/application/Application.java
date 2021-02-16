package com.netflix.spinnaker.front50.model.application;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.front50.model.Timestamped;
import java.util.*;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application implements Timestamped {

  private static final Logger log = LoggerFactory.getLogger(Application.class);

  private static final Joiner COMMA_JOINER = Joiner.on(',');

  private String name;
  private String description;
  private String email;
  private String updateTs;
  private String createTs;
  private String lastModifiedBy;
  private Object cloudProviders;
  private Map<String, Object> details = new HashMap<>();

  public String getCloudProviders() {
    // Orca expects a String
    return cloudProviders instanceof List
        ? COMMA_JOINER.join((List<String>) cloudProviders)
        : (String) cloudProviders;
  }

  public String getName() {
    // there is an expectation that application names are uppercased (historical)
    return Optional.ofNullable(name).map(it -> it.toUpperCase().trim()).orElse(null);
  }

  public List<TrafficGuard> getTrafficGuards() {
    final List<TrafficGuard> guards = (List<TrafficGuard>) details.get("trafficGuards");
    return (guards == null) ? new ArrayList<>() : guards;
  }

  public void setTrafficGuards(List<TrafficGuard> trafficGuards) {
    set("trafficGuards", trafficGuards);
  }

  @JsonAnyGetter
  public Map<String, Object> details() {
    return details;
  }

  @JsonAnySetter
  public void set(String name, Object value) {
    details.put(name, value);
  }

  @JsonIgnore
  public Map<String, Object> getPersistedProperties() {
    LinkedHashMap<String, Object> map = new LinkedHashMap<>(7);
    map.put("name", this.name);
    map.put("description", this.description);
    map.put("email", this.email);
    map.put("updateTs", this.updateTs);
    map.put("createTs", this.createTs);
    map.put("details", this.details);
    map.put("cloudProviders", this.cloudProviders);
    return map;
  }

  @Override
  @JsonIgnore
  public String getId() {
    return name.toLowerCase();
  }

  @Override
  @JsonIgnore
  public Long getLastModified() {
    return Strings.isNullOrEmpty(updateTs) ? null : Long.valueOf(updateTs);
  }

  @Override
  public void setLastModified(Long lastModified) {
    this.updateTs = lastModified.toString();
  }

  @Override
  public void setCreatedAt(Long createdAt) {
    if (createdAt != null) {
      this.createTs = createdAt.toString();
    }
  }

  @Override
  public Long getCreatedAt() {
    return Strings.isNullOrEmpty(createTs) ? null : Long.valueOf(createTs);
  }

  @Override
  public String toString() {
    return "Application{"
        + "name='"
        + name
        + "\'"
        + ", description='"
        + description
        + "\'"
        + ", email='"
        + email
        + "\'"
        + ", updateTs='"
        + updateTs
        + "\'"
        + ", createTs='"
        + createTs
        + "\'"
        + ", lastModifiedBy='"
        + lastModifiedBy
        + "\'"
        + ", cloudProviders="
        + cloudProviders
        + "}";
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getUpdateTs() {
    return updateTs;
  }

  public void setUpdateTs(String updateTs) {
    this.updateTs = updateTs;
  }

  public String getCreateTs() {
    return createTs;
  }

  public void setCreateTs(String createTs) {
    this.createTs = createTs;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public void setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
  }

  public void setCloudProviders(Object cloudProviders) {
    this.cloudProviders = cloudProviders;
  }

  public static class Permission implements Timestamped {
    private String name;
    private Long lastModified;
    private String lastModifiedBy;
    private Permissions permissions = Permissions.EMPTY;

    @Override
    @JsonIgnore
    public String getId() {
      return name.toLowerCase();
    }

    @JsonSetter
    public void setRequiredGroupMembership(List<String> requiredGroupMembership) {
      log.warn(
          "Required group membership settings detected in application {} "
              + "Please update to `permissions` format.",
          StructuredArguments.value("application", name));

      if (!permissions.isRestricted()) { // Do not overwrite permissions if it contains values
        final Permissions.Builder b = new Permissions.Builder();
        requiredGroupMembership.forEach(
            it -> {
              b.add(Authorization.READ, it.trim().toLowerCase());
              b.add(Authorization.WRITE, it.trim().toLowerCase());
            });
        permissions = b.build();
      }
    }

    public Permission copy() {
      // It's OK to "copy" permissions without actually copying since the object is immutable.
      Permission permission = new Permission();
      permission.setName(name);
      permission.setLastModified(lastModified);
      permission.setLastModifiedBy(lastModifiedBy);
      permission.setPermissions(permissions);
      return permission;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Long getLastModified() {
      return lastModified;
    }

    public void setLastModified(Long lastModified) {
      this.lastModified = lastModified;
    }

    public String getLastModifiedBy() {
      return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
      this.lastModifiedBy = lastModifiedBy;
    }

    public Permissions getPermissions() {
      return permissions;
    }

    public void setPermissions(Permissions permissions) {
      this.permissions = permissions;
    }
  }

  public static class TrafficGuard {

    private String account;
    private String stack;
    private String detail;
    private String location;
    private Boolean enabled = true;

    public String getAccount() {
      return account;
    }

    public void setAccount(String account) {
      this.account = account;
    }

    public String getStack() {
      return stack;
    }

    public void setStack(String stack) {
      this.stack = stack;
    }

    public String getDetail() {
      return detail;
    }

    public void setDetail(String detail) {
      this.detail = detail;
    }

    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }

    public Boolean getEnabled() {
      return enabled;
    }

    public void setEnabled(Boolean enabled) {
      this.enabled = enabled;
    }
  }
}
