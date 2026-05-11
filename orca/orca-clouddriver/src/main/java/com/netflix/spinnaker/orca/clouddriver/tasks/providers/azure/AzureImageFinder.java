package com.netflix.spinnaker.orca.clouddriver.tasks.providers.azure;

import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.ami.AppVersion;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageFinder;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AzureImageFinder implements ImageFinder {
  private static final Logger log = LoggerFactory.getLogger(AzureImageFinder.class);

  @Autowired OortService oortService;

  @Autowired ObjectMapper objectMapper;

  @Override
  public Collection<ImageDetails> byTags(
      StageExecution stage,
      String packageName,
      Map<String, String> tags,
      List<String> warningsCollector) {

    StageData stageData = (StageData) stage.mapTo(StageData.class);
    String account = stageData.account;
    List<String> regions = stageData.regions;

    if (regions == null || regions.isEmpty()) {
      throw new IllegalArgumentException("regions must be specified for Azure image search");
    }

    log.info(
        "Searching for Azure images with package: {} and tags: {} in regions: {} for account: {}",
        packageName,
        tags,
        regions,
        account);

    // Ask the controller for both managed and gallery image candidates --
    // managedImages=true here, galleryImages=true inherits from LookupOptions'
    // default. Source preference between the two (gallery wins on tie) is the
    // comparator's job (see AzureManagedImage#compareTo).
    Map<String, String> searchParams = new HashMap<>(prefixTags(tags));
    searchParams.put("managedImages", "true");

    List<AzureManagedImage> allMatchedImages =
        Retrofit2SyncCall.execute(
                oortService.findImage(getCloudProvider(), packageName, account, null, searchParams))
            .stream()
            .map(image -> objectMapper.convertValue(image, AzureManagedImage.class))
            .filter(image -> regions.contains(image.region))
            .sorted()
            .collect(Collectors.toList());

    if (allMatchedImages.isEmpty()) {
      return null;
    }

    Map<String, AzureManagedImage> latestImagesByRegion = new HashMap<>();
    for (AzureManagedImage image : allMatchedImages) {
      String region = image.region;
      if (!latestImagesByRegion.containsKey(region)
          || image.compareTo(latestImagesByRegion.get(region)) < 0) {
        latestImagesByRegion.put(region, image);
      }
    }

    List<ImageDetails> imageDetailsList = new ArrayList<>();
    for (AzureManagedImage image : latestImagesByRegion.values()) {
      imageDetailsList.add(image.toAzureImageDetails());
    }

    return imageDetailsList;
  }

  @Override
  public String getCloudProvider() {
    return "azure";
  }

  static Map<String, String> prefixTags(Map<String, String> tags) {
    return tags.entrySet().stream()
        .collect(toMap(entry -> "tag:" + entry.getKey(), Map.Entry::getValue));
  }

  static class StageData {
    @JsonProperty String account;
    @JsonProperty List<String> regions;
    @JsonProperty String packageName;
    @JsonProperty Map<String, String> tags;
  }

  static class AzureManagedImage implements Comparable<AzureManagedImage> {
    @JsonProperty String imageName;
    @JsonProperty String resourceGroup;
    @JsonProperty String region;
    @JsonProperty String osType;
    @JsonProperty String uri;
    // Carries a real semver only for Shared Image Gallery results, where all
    // versions of an image definition share the same `imageName` (=
    // imageDefinitionName) and the version is what distinguishes them.
    // Managed-image results carry the literal "na" sentinel that the
    // controller stamps on (AzureVMImageLookupController#buildAzureNamedImage
    // for managed VM images). Discriminate via `isGalleryVersion`, not raw
    // null/empty checks.
    @JsonProperty String version;
    @JsonProperty Map<String, Object> attributes;
    @JsonProperty Map<String, String> tags;

    ImageDetails toAzureImageDetails() {
      AppVersion appVersion = AppVersion.parseName(tags.get("appversion"));
      JenkinsDetails jenkinsDetails =
          Optional.ofNullable(appVersion)
              .map(
                  av ->
                      new JenkinsDetails(
                          tags.get("build_host"), av.getBuildJobName(), av.getBuildNumber()))
              .orElse(null);

      return new AzureImageDetails(
          imageName, region, resourceGroup, osType, uri,
          isGalleryVersion(version) ? version : null, jenkinsDetails);
    }

    @Override
    public int compareTo(AzureManagedImage other) {
      // Source preference: when both a gallery image and a managed image are
      // candidates in the same region, gallery wins. Gallery is the canonical
      // replicated/deploy-time form on Azure, and a lex compare on imageName
      // would otherwise be unsafe -- a stable gallery imageDefinitionName like
      // "moderne-arm64-noble" can sort either side of a timestamped managed
      // name like "moderne-1746961200000-noble-arm64". Gallery rows expose a
      // real semver in `version`; managed rows carry the controller's "na"
      // sentinel, which is what we discriminate on here.
      boolean thisIsGallery = isGalleryVersion(this.version);
      boolean otherIsGallery = isGalleryVersion(other.version);
      if (thisIsGallery != otherIsGallery) {
        return thisIsGallery ? -1 : 1;
      }

      // Same source. Sort by name first (reverse alphabetical to get latest versions).
      int byName = other.imageName.compareTo(this.imageName);
      if (byName != 0) {
        return byName;
      }
      // Names tie -- this is the gallery-image case, where every version of
      // an image definition reports the same imageName. Without a tiebreaker
      // the dedup-per-region loop downstream picks a random version. Compare
      // by `version` (descending) so the highest version wins.
      return compareVersions(other.version, this.version);
    }
  }

  // True iff `version` is a real Shared Image Gallery semver. Excludes the
  // "na" sentinel that AzureVMImageLookupController#buildAzureNamedImage
  // stamps on managed-image rows, alongside null/empty.
  static boolean isGalleryVersion(String version) {
    return version != null && !version.isEmpty() && !"na".equals(version);
  }

  // Numeric-aware comparison for Shared Image Gallery versions, which Azure
  // requires to be `MAJOR.MINOR.PATCH` integers. Falls back to lexicographic
  // comparison for any non-numeric component so an unexpected format degrades
  // gracefully instead of throwing.
  static int compareVersions(String a, String b) {
    if (a == null || a.isEmpty()) {
      return (b == null || b.isEmpty()) ? 0 : -1;
    }
    if (b == null || b.isEmpty()) {
      return 1;
    }
    String[] aParts = a.split("\\.");
    String[] bParts = b.split("\\.");
    int n = Math.max(aParts.length, bParts.length);
    for (int i = 0; i < n; i++) {
      String aPart = i < aParts.length ? aParts[i] : "0";
      String bPart = i < bParts.length ? bParts[i] : "0";
      int cmp;
      try {
        cmp = Long.compare(Long.parseLong(aPart), Long.parseLong(bPart));
      } catch (NumberFormatException e) {
        cmp = aPart.compareTo(bPart);
      }
      if (cmp != 0) {
        return cmp;
      }
    }
    return 0;
  }

  static class AzureImageDetails extends HashMap<String, Object> implements ImageDetails {
    AzureImageDetails(
        String imageName,
        String region,
        String resourceGroup,
        String osType,
        String uri,
        String version,
        JenkinsDetails jenkinsDetails) {
      put("imageName", imageName);
      String imageId = (uri != null && !uri.isEmpty() && !uri.equals("na")) ? uri : "na";
      put("imageId", imageId);
      put("region", region);
      put("resourceGroup", resourceGroup);
      put("osType", osType);

      if (version != null && !version.isEmpty()) {
        put("version", version);
      }

      if (jenkinsDetails != null) {
        put("jenkins", jenkinsDetails);
      }
    }

    @Override
    public String getImageId() {
      return (String) get("imageId");
    }

    @Override
    public String getImageName() {
      return (String) get("imageName");
    }

    @Override
    public String getRegion() {
      return (String) get("region");
    }

    @Override
    public JenkinsDetails getJenkins() {
      return (JenkinsDetails) get("jenkins");
    }
  }
}
