package com.netflix.spinnaker.igor.jenkins.client.model;

import com.netflix.spinnaker.igor.build.model.TestResult;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

/** Represents a build artifact */
@Root(name = "action", strict = false)
public class TestResults implements TestResult {
  public int getFailCount() {
    return failCount;
  }

  public void setFailCount(int failCount) {
    this.failCount = failCount;
  }

  public int getSkipCount() {
    return skipCount;
  }

  public void setSkipCount(int skipCount) {
    this.skipCount = skipCount;
  }

  public int getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(int totalCount) {
    this.totalCount = totalCount;
  }

  public String getUrlName() {
    return urlName;
  }

  public void setUrlName(String urlName) {
    this.urlName = urlName;
  }

  @Element(required = false)
  private int failCount;

  @Element(required = false)
  private int skipCount;

  @Element(required = false)
  private int totalCount;

  @Element(required = false)
  private String urlName;
}
