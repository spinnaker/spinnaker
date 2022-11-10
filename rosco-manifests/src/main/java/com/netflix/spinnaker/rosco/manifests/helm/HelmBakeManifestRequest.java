package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HelmBakeManifestRequest extends BakeManifestRequest {
  String namespace;

  /**
   * The 0th element is (or contains) the template/helm chart. The rest (possibly none) are values
   * files.
   */
  List<Artifact> inputArtifacts;

  boolean rawOverrides;

  /**
   * Helm v3 adds a new flag to include custom resource definition manifests in the templated
   * output. In the previous versions crds were usually included as part of templates, so the `helm
   * template` command always included them in the rendered output.
   */
  boolean includeCRDs;

  /**
   * When the helm chart is (in) a git/repo artifact, the path to the chart.
   *
   * <p>null/unspecified means the chart is in the root directory of the artifact.
   *
   * <p>If a git/repo artifact specifies a location (e.g. foo/bar), and the full path to Chart.yaml
   * is foo/bar/Chart.yaml, helmChartFilePath needs to be foo/bar/Chart.yaml, just as if the
   * git/repo didn't specify a location.
   *
   * <p>Because the same artifact might contain multiple charts (e.g. foo/bar/chart_one/Chart.yaml
   * and foo/bar/chart_two/Chart.yaml), the artifact's location isn't always sufficient to find the
   * chart.
   */
  String helmChartFilePath;
}
