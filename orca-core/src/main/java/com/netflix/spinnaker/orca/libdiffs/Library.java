package com.netflix.spinnaker.orca.libdiffs;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;

public class Library {
  public Library(String filePath, String name, String version, String org, String status) {
    this.filePath = filePath;
    this.name = name;
    this.version = version;
    this.org = org;
    this.buildDate = buildDate;
    this.status = status;
  }

  public boolean equals(Object o) {
    if (DefaultGroovyMethods.is(this, o)) return true;
    if (!(o instanceof Library)) return false;

    Library library = (Library) o;

    if (!name.equals(library.name)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (name != null ? name.hashCode() : 0);
    return result;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getOrg() {
    return org;
  }

  public void setOrg(String org) {
    this.org = org;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getBuildDate() {
    return buildDate;
  }

  public void setBuildDate(String buildDate) {
    this.buildDate = buildDate;
  }

  private String filePath;
  private String name;
  private String version;
  private String org;
  private String status;
  private String buildDate;
}
