package com.netflix.spinnaker.rosco.controllers;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import groovy.util.logging.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class V2BakeryController {
  @Autowired
  BakeManifestService bakeManifestService;

  @RequestMapping(value = "/api/v2/manifest/bake", method = RequestMethod.POST)
  Artifact doBake(@RequestBody BakeManifestRequest bakeRequest) {
    return bakeManifestService.bake(bakeRequest);
  }
}
