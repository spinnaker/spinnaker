import { IController, IScope } from 'angular';
import { loadAll } from 'js-yaml';

import {
  IKubernetesManifestCommandMetadata,
  IKubernetesManifestCommandData,
  KubernetesManifestCommandBuilder,
} from '../../../manifest/manifestCommandBuilder.service';

import { ExpectedArtifactService, IExpectedArtifact, ArtifactTypePatterns } from '@spinnaker/core';

export class KubernetesV2DeployManifestConfigCtrl implements IController {
  public state = {
    loaded: false,
  };

  public metadata: IKubernetesManifestCommandMetadata;
  public textSource = 'text';
  public artifactSource = 'artifact';
  public sources = [this.textSource, this.artifactSource];
  public excludedManifestArtifactPatterns = [ArtifactTypePatterns.KUBERNETES, ArtifactTypePatterns.DOCKER_IMAGE];

  public expectedArtifacts: IExpectedArtifact[];

  constructor(private $scope: IScope) {
    'ngInject';
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.$scope.application,
      this.$scope.stage.manifests || this.$scope.stage.manifest,
      this.$scope.stage.moniker,
    ).then((builtCommand: IKubernetesManifestCommandData) => {
      if (this.$scope.stage.isNew) {
        Object.assign(this.$scope.stage, builtCommand.command);
        this.$scope.stage.source = this.textSource;
      }

      if (!this.$scope.stage.manifestArtifactAccount) {
        this.$scope.stage.manifestArtifactAccount = '';
      }

      this.metadata = builtCommand.metadata;
      this.state.loaded = true;
    });

    this.expectedArtifacts = ExpectedArtifactService.getExpectedArtifactsAvailableToStage(
      $scope.stage,
      $scope.$parent.pipeline,
    );
  }

  public change() {
    this.$scope.ctrl.metadata.yamlError = false;
    try {
      this.$scope.stage.manifests = [];
      loadAll(this.metadata.manifestText, doc => {
        if (Array.isArray(doc)) {
          doc.forEach(d => this.$scope.stage.manifests.push(d));
        } else {
          this.$scope.stage.manifests.push(doc);
        }
      });
    } catch (e) {
      this.$scope.ctrl.metadata.yamlError = true;
    }
  }
}
