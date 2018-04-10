import { module } from 'angular';

import {
  ArtifactReferenceServiceProvider,
  PIPELINE_CONFIG_PROVIDER,
  PipelineConfigProvider,
  SETTINGS
} from 'core';

import { BakeManifestConfigCtrl } from './bakeManifestConfig.controller';

export const BAKE_MANIFEST_STAGE = 'spinnaker.core.pipeline.stage.bakeManifestStage';

module(BAKE_MANIFEST_STAGE, [
  PIPELINE_CONFIG_PROVIDER,
]).config((pipelineConfigProvider: PipelineConfigProvider, artifactReferenceServiceProvider: ArtifactReferenceServiceProvider) => {
  if (SETTINGS.feature.versionedProviders) {
    pipelineConfigProvider.registerStage({
      label: 'Bake (Manifest)',
      description: 'Bake a manifest (or multi-doc manifest set) using a template renderer such as Helm.',
      key: 'bakeManifest',
      templateUrl: require('./bakeManifestConfig.html'),
      controller: 'BakeManifestConfigCtrl',
      producesArtifacts: true,
      cloudProvider: 'kubernetes',
      controllerAs: 'ctrl',
    });

    artifactReferenceServiceProvider.registerReference('stage', () => [
      ['expectedArtifactId'],
    ]);
  }
}).controller('BakeManifestConfigCtrl', BakeManifestConfigCtrl);
