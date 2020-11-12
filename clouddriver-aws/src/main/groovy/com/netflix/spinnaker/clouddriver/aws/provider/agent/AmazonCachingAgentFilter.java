package com.netflix.spinnaker.clouddriver.aws.provider.agent;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("aws.caching.filter")
public class AmazonCachingAgentFilter {

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

  public boolean hasTagFilter() {
    return this.hasIncludeTagFilter() || this.hasExcludeTagFilter();
  }

  public boolean hasIncludeTagFilter() {
    return this.includeTags != null && this.includeTags.size() > 0;
  }

  public boolean hasExcludeTagFilter() {
    return this.excludeTags != null && this.excludeTags.size() > 0;
  }

  /**
   * ResourceTag is a helper wrapper for AWS resources which are taggable, to convert their specific
   * types from the AWS SDK into a generic type for comparison by this agent filter.
   */
  public static class ResourceTag {
    final String key;
    final String value;

    public ResourceTag(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  /**
   * Determine if the resource with the given set of tags should be retained, that is, if the caller
   * should discard or keep it based on the include/exclude filters configured.
   */
  public boolean shouldRetainResource(List<ResourceTag> tags) {

    // retain the resource by default if there isn't an include filter setup
    boolean retainResource = !this.hasIncludeTagFilter();

    if (this.hasIncludeTagFilter()) {
      retainResource =
          this.includeTags.stream()
              .anyMatch(
                  filter ->
                      tags.stream()
                          .anyMatch(tag -> tagFilterOptionMatchesResourceTag(filter, tag)));
    }

    // exclude takes precedence over include so runs second if the resource is still being retained
    if (retainResource && this.hasExcludeTagFilter()) {
      retainResource =
          this.excludeTags.stream()
              .noneMatch(
                  filter ->
                      tags.stream()
                          .anyMatch(tag -> tagFilterOptionMatchesResourceTag(filter, tag)));
    }

    return retainResource;
  }

  private boolean tagFilterOptionMatchesResourceTag(
      TagFilterOption tagFilterOption, ResourceTag resourceTag) {
    if (resourceTag.key == null || !resourceTag.key.matches(tagFilterOption.name)) {
      return false;
    }

    return StringUtils.isEmpty(tagFilterOption.getValue())
        || (resourceTag.value != null && resourceTag.value.matches(tagFilterOption.value));
  }
}
