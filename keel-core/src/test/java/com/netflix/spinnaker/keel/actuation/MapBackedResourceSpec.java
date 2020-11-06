package com.netflix.spinnaker.keel.actuation;

import com.netflix.spinnaker.keel.api.ResourceSpec;
import java.util.Map;
import javax.annotation.Nonnull;

public class MapBackedResourceSpec implements ResourceSpec {
  private String id;
  private String application;
  private Map<String, Object> data;

  public MapBackedResourceSpec(String id, String application, Map<String, Object> data) {
    this.id = id;
    this.application = application;
    this.data = data;
  }

  @Nonnull
  @Override
  public String getId() {
    return id;
  }

  @Nonnull
  @Override
  public String getApplication() {
    return application;
  }

  @Nonnull
  @Override
  public String getDisplayName() {
    return application + "-" + id;
  }

  public Map<String, Object> getData() {
    return data;
  }
}
