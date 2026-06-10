package io.moderne.spinnaker.kork.atlas;

import com.netflix.frigga.Names;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.kohsuke.randname.RandomNameGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeTagsRequest;
import software.amazon.awssdk.services.ec2.model.Filter;

public final class Ec2CommonTags {

  private Ec2CommonTags() {}

  private static final Logger log = LoggerFactory.getLogger(Ec2CommonTags.class);

  static Map<String, String> fallbackTags(String applicationName) {
    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("cloud.provider", "none");
    tags.put("application", applicationName);
    tags.put("environment", "local");
    tags.put("instance.id", hostname());
    tags.put("instance.display.name", new RandomNameGenerator().next());
    return tags;
  }

  static Map<String, String> friggaTagsFromAsgName(String asgName) {
    Names names = Names.parseName(asgName);
    Map<String, String> tags = new LinkedHashMap<>();
    putIfNotBlank(tags, "application", names.getApp());
    putIfNotBlank(tags, "cluster", names.getCluster());
    putIfNotBlank(tags, "stack", names.getStack());
    String detail = names.getDetail();
    tags.put("detail", (detail != null && !detail.isBlank()) ? detail : "none");
    putIfNotBlank(tags, "server.group", names.getGroup());
    return tags;
  }

  private static void putIfNotBlank(Map<String, String> tags, String key, String value) {
    if (value != null && !value.isBlank()) {
      tags.put(key, value);
    }
  }

  public static Tags derive(String applicationName) {
    Map<String, String> map;
    try {
      map = imdsReachable() ? ec2Tags(applicationName) : fallbackTags(applicationName);
    } catch (Exception | LinkageError e) {
      // Defense-in-depth: any unhandled throw from EC2MetadataUtils (internal AWS SDK API,
      // e.g. SdkClientException when AWS_EC2_METADATA_DISABLED=true) or Ec2Client class loading
      // (e.g. NoClassDefFoundError if a transitive SDK module is missing) must not break Spring
      // context startup. LinkageError is included alongside Exception because classpath drift
      // around the AWS SDK is a realistic failure mode; JVM-fatal Errors (OOM, StackOverflow)
      // are deliberately not caught.
      log.warn("Failed to derive EC2 common tags; falling back to dev tags", e);
      map = fallbackTags(applicationName);
    }
    return Tags.of(map.entrySet().stream().map(e -> Tag.of(e.getKey(), e.getValue())).toList());
  }

  private static boolean imdsReachable() {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();
      HttpRequest tokenRequest =
          HttpRequest.newBuilder()
              .uri(URI.create("http://169.254.169.254/latest/api/token"))
              .timeout(Duration.ofMillis(500))
              .header("X-aws-ec2-metadata-token-ttl-seconds", "21600")
              .PUT(HttpRequest.BodyPublishers.noBody())
              .build();
      HttpResponse<String> response =
          client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  static Map<String, String> ec2Tags(String applicationName) {
    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("cloud.provider", "aws");

    String instanceId = EC2MetadataUtils.getInstanceId();
    if (instanceId != null) {
      tags.put("instance.id", instanceId);
    }
    String region = EC2MetadataUtils.getEC2InstanceRegion();
    tags.put("region", region != null ? region : "unknown");
    String az = EC2MetadataUtils.getAvailabilityZone();
    if (az != null) {
      tags.put("availability.zone", az);
    }
    tags.put("instance.display.name", new RandomNameGenerator().next());

    // Discover ASG name via DescribeTags filtered on the instance id;
    // Frigga-parse it to fill in application/cluster/stack/detail/server.group.
    // Skip when instanceId or region is missing: instanceId-null would NPE in Filter builder
    // validation and would burn an EC2 API call against a throttled endpoint; region-null would
    // make Ec2Client.builder() re-walk the SDK region-resolution chain (incl. another IMDS hit
    // with longer SDK timeouts), which on a flaky-IMDS instance hangs startup for seconds.
    if (instanceId != null && region != null) {
      try (Ec2Client ec2Client = Ec2Client.builder().region(Region.of(region)).build()) {
        DescribeTagsRequest request =
            DescribeTagsRequest.builder()
                .filters(Filter.builder().name("resource-id").values(instanceId).build())
                .build();
        ec2Client.describeTags(request).tags().stream()
            .filter(tag -> "aws:autoscaling:groupName".equals(tag.key()))
            .findFirst()
            .ifPresent(tag -> tags.putAll(friggaTagsFromAsgName(tag.value())));
      } catch (Exception e) {
        log.warn("Failed to fetch ASG name via DescribeTags", e);
      }
    }

    // If Frigga didn't surface an application (helper skips null/blank entries entirely),
    // fall back to spring.application.name so the application tag is never missing.
    tags.putIfAbsent("application", applicationName);

    return tags;
  }

  private static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "localhost";
    }
  }
}
