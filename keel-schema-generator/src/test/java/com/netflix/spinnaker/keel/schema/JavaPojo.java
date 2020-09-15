package com.netflix.spinnaker.keel.schema;

public class JavaPojo {
  public JavaPojo(String string) {
    this.string = string;
  }

  private final String string;

  public String getString() {
    return string;
  }
}
