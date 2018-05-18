import { module } from 'angular';
import { get } from 'lodash';

import { ArtifactReferenceService, Registry, SETTINGS } from 'core';

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
      });

      ArtifactReferenceService.registerReference('stage', stage => {
        if (stage.type !== 'bakeManifest') {
          return [];
        }
        const paths = [['expectedArtifactId']];
        const expected = get(stage, 'expectedArtifacts', []);
        const inputs = get(stage, 'inputArtifacts', []);
        expected.forEach((_e, i) => {
          paths.push(['expectedArtifacts', String(i), 'id']);
        });
        inputs.forEach((_in, i) => {
          paths.push(['inputArtifacts', String(i), 'id']);
        });
        return paths;
      });
    }
  })
  .controller('BakeManifestConfigCtrl', BakeManifestConfigCtrl);
