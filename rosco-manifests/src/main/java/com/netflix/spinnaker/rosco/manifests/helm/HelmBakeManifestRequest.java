package com.netflix.spinnaker.rosco.manifests.helm;

import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HelmBakeManifestRequest extends BakeManifestRequest {
  String namespace;
}
