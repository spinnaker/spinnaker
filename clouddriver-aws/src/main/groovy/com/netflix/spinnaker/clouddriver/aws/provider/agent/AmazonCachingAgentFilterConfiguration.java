package com.netflix.spinnaker.clouddriver.aws.provider.agent;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("aws.caching.filter")
public class AmazonCachingAgentFilterConfiguration {

  List<TagFilterOption> includeTags;
  List<TagFilterOption> excludeTags;

  public static class TagFilterOption {
    String name;
    String value;

    public TagFilterOption() {}

    public TagFilterOption(String name, String value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }

  public List<TagFilterOption> getIncludeTags() {
    return includeTags;
  }

  public void setIncludeTags(List<TagFilterOption> includeTags) {
    this.includeTags = includeTags;
  }

  public List<TagFilterOption> getExcludeTags() {
    return excludeTags;
  }

  public void setExcludeTags(List<TagFilterOption> excludeTags) {
    this.excludeTags = excludeTags;
  }
}
