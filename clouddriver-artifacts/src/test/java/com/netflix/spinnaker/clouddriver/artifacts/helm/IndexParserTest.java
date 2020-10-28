package com.netflix.spinnaker.clouddriver.artifacts.helm;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class IndexParserTest {

  private String buildIndexYaml(String chartName, String version, List<String> urls) {
    StringBuilder indexYamlBuilder = new StringBuilder();
    indexYamlBuilder
        .append("---\n")
        .append("apiVersion: '1.0'\n")
        .append("entries:\n")
        .append("  ")
        .append(chartName)
        .append(":\n")
        .append("  - name: ")
        .append(chartName)
        .append("\n")
        .append("    version: ")
        .append(version)
        .append("\n")
        .append("    urls:\n");
    urls.forEach(url -> indexYamlBuilder.append("    - ").append(url).append("\n"));
    return indexYamlBuilder.toString();
  }

  private String addIndexEntry(
      String index, String chartName, String newVersion, List<String> urls) {
    StringBuilder indexYamlBuilder = new StringBuilder(index);
    indexYamlBuilder
        .append("  - name: ")
        .append(chartName)
        .append("\n")
        .append("    version: ")
        .append(newVersion)
        .append("\n")
        .append("    urls:\n");
    urls.forEach(url -> indexYamlBuilder.append("    - ").append(url).append("\n"));
    return indexYamlBuilder.toString();
  }

  @Test
  public void findUrlsShouldResolveRelativeChartUrls() throws IOException {
    IndexParser parser = new IndexParser("http://localhost/test/");

    String indexYaml =
        buildIndexYaml("test-chart1", "0.0.1", Arrays.asList("test-chart1-0.0.1.tgz"));
    try (InputStream is = new ByteArrayInputStream(indexYaml.getBytes())) {
      List<String> actualUrls = parser.findUrls(is, "test-chart1", "0.0.1");
      assertThat(actualUrls).containsOnly("http://localhost/test/test-chart1-0.0.1.tgz");
    }
  }

  @Test
  public void findUrlsShouldResolveRelativeChartUrlsIfTrailingSlashMissingFromRepositoryUrl()
      throws IOException {
    IndexParser parser = new IndexParser("http://localhost/test");

    String indexYaml =
        buildIndexYaml("test-chart1", "0.0.1", Arrays.asList("test-chart1-0.0.1.tgz"));
    try (InputStream is = new ByteArrayInputStream(indexYaml.getBytes())) {
      List<String> actualUrls = parser.findUrls(is, "test-chart1", "0.0.1");
      assertThat(actualUrls).containsOnly("http://localhost/test/test-chart1-0.0.1.tgz");
    }
  }

  @Test
  public void findUrlsShouldHandleAbsoluteChartUrls() throws IOException {
    IndexParser parser = new IndexParser("http://localhost/test/");

    String indexYaml =
        buildIndexYaml(
            "test-chart1",
            "0.0.1",
            Arrays.asList("https://absolute.url/test/test-chart1-0.0.1.tgz"));
    try (InputStream is = new ByteArrayInputStream(indexYaml.getBytes())) {
      List<String> actualUrls = parser.findUrls(is, "test-chart1", "0.0.1");
      assertThat(actualUrls).containsOnly("https://absolute.url/test/test-chart1-0.0.1.tgz");
    }
  }

  @Test
  public void findUrlsShouldHandleMixedChartUrls() throws IOException {
    IndexParser parser = new IndexParser("http://localhost/test/");

    String indexYaml =
        buildIndexYaml(
            "test-chart1",
            "0.0.1",
            Arrays.asList(
                "https://absolute.url/test/test-chart1-0.0.1.tgz", "test-chart1-0.0.1.tgz"));
    try (InputStream is = new ByteArrayInputStream(indexYaml.getBytes())) {
      List<String> actualUrls = parser.findUrls(is, "test-chart1", "0.0.1");
      assertThat(actualUrls)
          .containsExactlyInAnyOrder(
              "https://absolute.url/test/test-chart1-0.0.1.tgz",
              "http://localhost/test/test-chart1-0.0.1.tgz");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.0.10", "1.0.90"})
  public void findUrlsShouldFindLatestNumericVersion(String maxVersion) throws IOException {
    IndexParser parser = new IndexParser("http://localhost/test/");

    String indexYaml =
        buildIndexYaml("test-chart1", "1.0.9", Arrays.asList("test-chart1-1.0.9.tgz"));
    indexYaml =
        addIndexEntry(
            indexYaml,
            "test-chart1",
            maxVersion,
            Arrays.asList("test-chart1-" + maxVersion + ".tgz"));
    try (InputStream is = new ByteArrayInputStream(indexYaml.getBytes())) {
      List<String> actualUrls = parser.findUrls(is, "test-chart1", null);
      assertThat(actualUrls)
          .containsOnly("http://localhost/test/test-chart1-" + maxVersion + ".tgz");
    }
  }

  @Test
  public void findUrlsShouldFindLatestNonNumericVersion() throws IOException {
    IndexParser parser = new IndexParser("http://localhost/test/");

    String indexYaml = buildIndexYaml("test-chart1", "abc", Arrays.asList("test-chart1-abc.tgz"));
    indexYaml =
        addIndexEntry(indexYaml, "test-chart1", "def", Arrays.asList("test-chart1-def.tgz"));
    try (InputStream is = new ByteArrayInputStream(indexYaml.getBytes())) {
      List<String> actualUrls = parser.findUrls(is, "test-chart1", null);
      assertThat(actualUrls).containsOnly("http://localhost/test/test-chart1-def.tgz");
    }
  }
}
