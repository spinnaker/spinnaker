import { IController, IScope } from 'angular';
import { loadAll } from 'js-yaml';
import { ExpectedArtifactService, IExpectedArtifact } from '@spinnaker/core';
import { IPatchOptions, MergeStrategy } from './patchOptionsForm.component';
import {
  IKubernetesManifestCommandMetadata,
  KubernetesManifestCommandBuilder,
} from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

export class KubernetesV2PatchManifestConfigCtrl implements IController {
  public state = {
    loaded: false,
  };

  public metadata: IKubernetesManifestCommandMetadata;
  public textSource = 'text';
  public artifactSource = 'artifact';
  public sources = [this.textSource, this.artifactSource];

  public expectedArtifacts: IExpectedArtifact[];

  constructor(private $scope: IScope) {
    'ngInject';

    this.expectedArtifacts = ExpectedArtifactService.getExpectedArtifactsAvailableToStage(
      $scope.stage,
      $scope.$parent.pipeline,
    );

    const defaultOptions: IPatchOptions = {
      mergeStrategy: MergeStrategy.strategic,
      record: true,
    };

    if (this.$scope.stage.isNew) {
      this.$scope.stage.options = defaultOptions;
    }

    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.$scope.application,
      this.$scope.stage.patchBody,
      this.$scope.stage.moniker,
    ).then(builtCommand => {
      if (this.$scope.stage.isNew) {
        Object.assign(this.$scope.stage, {
          account: builtCommand.command.account,
          manifestArtifactId: builtCommand.command.manifestArtifactId,
          manifestArtifactAccount: builtCommand.command.manifestArtifactAccount,
          patchBody: builtCommand.command.manifests || builtCommand.command.manifest,
          source: this.textSource,
          location: '',
          cloudProvider: 'kubernetes',
        });
      }

      this.metadata = builtCommand.metadata;
      this.state.loaded = true;
    });
  }

  public change() {
    this.$scope.ctrl.metadata.yamlError = false;
    try {
      this.$scope.stage.patchBody = {};
      loadAll(this.metadata.manifestText, doc => {
        if (Array.isArray(doc)) {
          // TODO: Error. Not a valid use case for patch.
          doc.forEach(d => this.$scope.stage.patchBody.push(d));
        } else {
          this.$scope.stage.patchBody = doc;
        }
      });
    } catch (e) {
      this.$scope.ctrl.metadata.yamlError = true;
    }
  }
}
