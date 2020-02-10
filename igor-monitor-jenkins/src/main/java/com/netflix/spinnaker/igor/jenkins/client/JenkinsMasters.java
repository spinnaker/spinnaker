package com.netflix.spinnaker.igor.jenkins.client;

import com.netflix.spinnaker.igor.jenkins.JenkinsService;
import java.util.Map;

/** Wrapper class for a collection of jenkins clients */
public class JenkinsMasters {
  public Map<String, JenkinsService> getMap() {
    return map;
  }

  public void setMap(Map<String, JenkinsService> map) {
    this.map = map;
  }

  private Map<String, JenkinsService> map;
}
