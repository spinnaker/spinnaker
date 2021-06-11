package com.netflix.spinnaker.front50.model.notification;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.front50.api.model.Timestamped;
import java.util.HashMap;

public class Notification extends HashMap<String, Object> implements Timestamped {

  public static final String GLOBAL_ID = "__GLOBAL";

  @Override
  @JsonIgnore
  public String getId() {
    return (String) super.getOrDefault("application", GLOBAL_ID);
  }

  @Override
  @JsonIgnore
  public Long getLastModified() {
    return (Long) super.get("lastModified");
  }

  @Override
  public void setLastModified(Long lastModified) {
    super.put("lastModified", lastModified);
  }

  @Override
  public String getLastModifiedBy() {
    return (String) super.get("lastModifiedBy");
  }

  @Override
  public void setLastModifiedBy(String lastModifiedBy) {
    super.put("lastModifiedBy", lastModifiedBy);
  }
}
