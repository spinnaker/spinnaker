package com.netflix.spinnaker.rosco.controllers;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestService;
import groovy.util.logging.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class V2BakeryController {
  @Autowired HelmBakeManifestService helmBakeManifestService;

  @RequestMapping(value = "/api/v2/manifest/bake/helm", method = RequestMethod.POST)
  Artifact doBake(@RequestBody HelmBakeManifestRequest bakeRequest) {
    return helmBakeManifestService.bake(bakeRequest);
  }
}
