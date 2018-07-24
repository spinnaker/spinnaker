import { module } from 'angular';
import { get } from 'lodash';

import {
  ArtifactReferenceService,
  ExecutionArtifactTab,
  ExecutionDetailsTasks,
  ExpectedArtifactService,
  IArtifact,
  IStage,
  Registry,
  SETTINGS,
} from 'core';

import { BakeManifestConfigCtrl } from './bakeManifestConfig.controller';

export const BAKE_MANIFEST_STAGE = 'spinnaker.core.pipeline.stage.bakeManifestStage';

module(BAKE_MANIFEST_STAGE, [])
  .config(() => {
    if (SETTINGS.feature.versionedProviders) {
      Registry.pipeline.registerStage({
        label: 'Bake (Manifest)',
        description: 'Bake a manifest (or multi-doc manifest set) using a template renderer such as Helm.',
        key: 'bakeManifest',
        templateUrl: require('./bakeManifestConfig.html'),
        controller: 'BakeManifestConfigCtrl',
        producesArtifacts: true,
        cloudProvider: 'kubernetes',
        controllerAs: 'ctrl',
        executionDetailsSections: [ExecutionDetailsTasks, ExecutionArtifactTab],
        artifactExtractor: (fields: string[]) =>
          ExpectedArtifactService.accumulateArtifacts<IArtifact>(['inputArtifacts'])(fields).map(
            (a: IArtifact) => a.id,
          ),
        artifactRemover: (stage: IStage, artifactId: string) => {
          ArtifactReferenceService.removeArtifactFromFields(['expectedArtifactId'])(stage, artifactId);

          const artifactMatches = (artifact: IArtifact) => artifact.id === artifactId;
          stage.expectedArtifacts = get(stage, 'expectedArtifacts', []).filter(a => !artifactMatches(a));
          stage.inputArtifacts = get(stage, 'inputArtifacts', []).filter(a => !artifactMatches(a));
        },
      });
    }
  })
  .controller('BakeManifestConfigCtrl', BakeManifestConfigCtrl);
