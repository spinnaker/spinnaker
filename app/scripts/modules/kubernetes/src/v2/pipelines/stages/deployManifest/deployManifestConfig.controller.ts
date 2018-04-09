import { IController, IScope } from 'angular';
import { loadAll } from 'js-yaml';

import {
  IKubernetesManifestCommandMetadata,
  KubernetesManifestCommandBuilder,
} from '../../../manifest/manifestCommandBuilder.service';

import { ExpectedArtifactService, IExpectedArtifact } from '@spinnaker/core';

export class KubernetesV2DeployManifestConfigCtrl implements IController {
  public state = {
    loaded: false,
  };

  public metadata: IKubernetesManifestCommandMetadata;
  public textSource = 'text';
  public artifactSource = 'artifact';
  public sources = [this.textSource, this.artifactSource];

  public expectedArtifacts: IExpectedArtifact[];

  constructor(
    private $scope: IScope,
    private kubernetesManifestCommandBuilder: KubernetesManifestCommandBuilder,
    private expectedArtifactService: ExpectedArtifactService,
  ) {
    'ngInject';
    this.kubernetesManifestCommandBuilder
      .buildNewManifestCommand(
        this.$scope.application,
        this.$scope.stage.manifests || this.$scope.stage.manifest,
        this.$scope.stage.moniker,
      )
      .then(builtCommand => {
        if (this.$scope.stage.isNew) {
          Object.assign(this.$scope.stage, builtCommand.command);
          this.$scope.stage.source = this.textSource;
        }

        this.metadata = builtCommand.metadata;
        this.state.loaded = true;
      });

    this.expectedArtifacts = this.expectedArtifactService.getExpectedArtifactsAvailableToStage(
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
