import { IController, IScope } from 'angular';
import { get, defaults } from 'lodash';
import { dump } from 'js-yaml';
import { ExpectedArtifactSelectorViewController, NgManifestArtifactDelegate } from '@spinnaker/core';
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
  public rawPatchBody: string;

  private manifestArtifactDelegate: NgManifestArtifactDelegate;
  private manifestArtifactController: ExpectedArtifactSelectorViewController;

  constructor(private $scope: IScope) {
    'ngInject';

    try {
      this.rawPatchBody = $scope.stage.patchBody ? dump($scope.stage.patchBody) : null;
    } catch (e) {
      this.rawPatchBody = null;
    }

    const defaultOptions: IPatchOptions = {
      mergeStrategy: MergeStrategy.strategic,
      record: true,
    };

    if (this.$scope.stage.isNew) {
      this.$scope.stage.options = defaultOptions;
    }

    this.manifestArtifactDelegate = new NgManifestArtifactDelegate($scope);
    this.manifestArtifactController = new ExpectedArtifactSelectorViewController(this.manifestArtifactDelegate);

    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.$scope.application,
      this.$scope.stage.patchBody,
      this.$scope.stage.moniker,
    ).then(builtCommand => {
      if (this.$scope.stage.isNew) {
        defaults(this.$scope.stage, {
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
      this.manifestArtifactDelegate.setAccounts(get(this, ['metadata', 'backingData', 'artifactAccounts']));
      this.manifestArtifactController.updateAccounts(this.manifestArtifactDelegate.getSelectedExpectedArtifact());
    });
  }

  public handleYamlChange = (rawPatchBody: string, patchBody: any): void => {
    this.rawPatchBody = rawPatchBody;
    this.$scope.stage.patchBody = patchBody;
    // Called from a React component.
    this.$scope.$applyAsync();
  };

  public canShowAccountSelect() {
    return (
      !this.$scope.showCreateArtifactForm &&
      (this.manifestArtifactController.accountsForArtifact.length > 1 &&
        this.manifestArtifactDelegate.getSelectedExpectedArtifact() != null)
    );
  }

  public handleManifestSelectorChange = (): void => {
    this.$scope.$applyAsync();
  };
}
