import { IController, IScope } from 'angular';
import { get, defaults } from 'lodash';
import { ExpectedArtifactSelectorViewController, NgGenericArtifactDelegate, IManifest } from '@spinnaker/core';

import {
  IKubernetesManifestCommandMetadata,
  IKubernetesManifestCommandData,
  KubernetesManifestCommandBuilder,
} from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

export class KubernetesV2DeployManifestConfigCtrl implements IController {
  public state = {
    loaded: false,
  };

  public metadata: IKubernetesManifestCommandMetadata;
  public textSource = 'text';
  public artifactSource = 'artifact';
  public sources = [this.textSource, this.artifactSource];

  public manifestArtifactDelegate: NgGenericArtifactDelegate;
  public manifestArtifactController: ExpectedArtifactSelectorViewController;

  public static $inject = ['$scope'];
  constructor(private $scope: IScope) {
    'ngInject';
    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.$scope.application,
      this.$scope.stage.manifests || this.$scope.stage.manifest,
      this.$scope.stage.moniker,
    ).then((builtCommand: IKubernetesManifestCommandData) => {
      if (this.$scope.stage.isNew) {
        defaults(this.$scope.stage, builtCommand.command, {
          manifestArtifactAccount: '',
          source: this.textSource,
        });
      }
      this.metadata = builtCommand.metadata;
      this.state.loaded = true;
      this.manifestArtifactDelegate.setAccounts(get(this, ['metadata', 'backingData', 'artifactAccounts'], []));
      this.manifestArtifactController.updateAccounts(this.manifestArtifactDelegate.getSelectedExpectedArtifact());
    });

    this.manifestArtifactDelegate = new NgGenericArtifactDelegate($scope, 'manifest');
    this.manifestArtifactController = new ExpectedArtifactSelectorViewController(this.manifestArtifactDelegate);
  }

  public canShowAccountSelect() {
    return (
      this.$scope.stage.source === this.artifactSource &&
      !this.$scope.showCreateArtifactForm &&
      (this.manifestArtifactController.accountsForArtifact.length > 1 &&
        this.manifestArtifactDelegate.getSelectedExpectedArtifact() != null)
    );
  }

  public handleCopy = (manifest: IManifest) => {
    this.$scope.stage.manifests = [manifest];
    // This method is called from a React component.
    this.$scope.$applyAsync();
  };
}
