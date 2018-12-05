package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.Objects;

public class Location {
  /**
   * @return The all lowercase, plural form of this location type ("regions", "zones" or "namespaces")
   */
  @JsonIgnore
  public String pluralType() {
    return this.type.toString().toLowerCase() + "s";
  }

  /**
   * @return The all lowercase, singular form of this location type ("region", "zone" or "namespace")
   */
  @JsonIgnore
  public String singularType() {
    return this.type.toString().toLowerCase();
  }

  public static Location zone(String value) {
    return new Location(Type.ZONE, value);
  }

  public static Location region(String value) {
    return new Location(Type.REGION, value);
  }

  public static Location namespace(String value) {
    return new Location(Type.NAMESPACE, value);
  }

  public Location(Type type, String value) {
    this.type = type;
    this.value = value;
  }

  public Location(HashMap args) {
    this(
      (Type) args.get("type"),
      (String) args.get("value"));
  }

  public final Type getType() {
    return type;
  }

  public final String getValue() {
    return value;
  }

  private final Type type;
  private final String value;

  public enum Type {
    REGION, NAMESPACE, ZONE
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Location location = (Location) o;
    return type == location.type &&
      Objects.equals(value, location.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, value);
  }

  @Override
  public String toString() {
    return "Location{" +
      "type=" + type +
      ", value='" + value + '\'' +
      '}';
  }
}
