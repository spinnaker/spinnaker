import { IController, IScope } from 'angular';
import { load } from 'js-yaml';

import {
  IKubernetesManifestCommandMetadata,
  KubernetesManifestCommandBuilder,
} from '../../../manifest/manifestCommandBuilder.service';

import { IExpectedArtifact, } from '@spinnaker/core'

export class KubernetesV2DeployManifestConfigCtrl implements IController {
  public state = {
    loaded: false,
  };

  public metadata: IKubernetesManifestCommandMetadata;
  public textSource = 'text';
  public artifactSource = 'artifact';
  public sources = [
    this.textSource,
    this.artifactSource,
  ];

  public expectedArtifacts: IExpectedArtifact[];

  constructor(private $scope: IScope,
              private kubernetesManifestCommandBuilder: KubernetesManifestCommandBuilder) {
    'ngInject';
    this.kubernetesManifestCommandBuilder.buildNewManifestCommand(this.$scope.application, this.$scope.stage.manifest, this.$scope.stage.moniker)
      .then((builtCommand) => {
        if (this.$scope.stage.isNew) {
          Object.assign(this.$scope.stage, builtCommand.command);
          this.$scope.stage.source = this.textSource;
        }

        this.metadata = builtCommand.metadata;
        this.state.loaded = true;
      });

    this.expectedArtifacts = $scope.$parent.pipeline.expectedArtifacts || [];
  }

  public change() {
    try {
      this.$scope.stage.manifest = load(this.metadata.manifestText);
    } catch (e) {}
  }
}
