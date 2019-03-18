import { IController, IScope } from 'angular';
import { get, defaults } from 'lodash';
import {
  ExpectedArtifactSelectorViewController,
  NgGenericArtifactDelegate,
  IManifest,
  IArtifact,
  IExpectedArtifact,
  ArtifactTypePatterns,
  SETTINGS,
} from '@spinnaker/core';

import {
  IKubernetesManifestCommandMetadata,
  IKubernetesManifestCommandData,
  KubernetesManifestCommandBuilder,
} from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

import { IManifestBindArtifact } from './ManifestBindArtifactsSelector';

export class KubernetesV2DeployManifestConfigCtrl implements IController {
  public state = {
    loaded: false,
  };

  public metadata: IKubernetesManifestCommandMetadata;
  public textSource = 'text';
  public artifactSource = 'artifact';
  public manifestArtifactDelegate: NgGenericArtifactDelegate;
  public manifestArtifactController: ExpectedArtifactSelectorViewController;
  public sources = [this.textSource, this.artifactSource];

  public static $inject = ['$scope'];

  constructor(private $scope: IScope) {
    this.manifestArtifactDelegate = new NgGenericArtifactDelegate($scope, 'manifest');
    this.manifestArtifactController = new ExpectedArtifactSelectorViewController(this.manifestArtifactDelegate);

    const stage = this.$scope.stage;
    this.$scope.bindings = (stage.requiredArtifactIds || [])
      .map((id: string) => ({ expectedArtifactId: id }))
      .concat((stage.requiredArtifacts || []).map((artifact: IArtifact) => ({ artifact: artifact })));

    this.$scope.excludedManifestArtifactTypes = [
      ArtifactTypePatterns.DOCKER_IMAGE,
      ArtifactTypePatterns.KUBERNETES,
      ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
    ];

    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.$scope.application,
      stage.manifests || stage.manifest,
      stage.moniker,
    ).then((builtCommand: IKubernetesManifestCommandData) => {
      if (stage.isNew) {
        defaults(stage, builtCommand.command, {
          manifestArtifactAccount: '',
          source: this.textSource,
          skipExpressionEvaluation: false,
        });
      }
      this.metadata = builtCommand.metadata;
      this.state.loaded = true;
      this.manifestArtifactDelegate.setAccounts(get(this, ['metadata', 'backingData', 'artifactAccounts'], []));
      this.manifestArtifactController.updateAccounts(this.manifestArtifactDelegate.getSelectedExpectedArtifact());
    });
  }

  public onManifestExpectedArtifactSelected = (expectedArtifact: IExpectedArtifact) => {
    this.$scope.$applyAsync(() => {
      this.$scope.stage.manifestArtifactId = expectedArtifact.id;
    });
  };

  public onManifestArtifactEdited = (artifact: IArtifact) => {
    this.$scope.$applyAsync(() => {
      this.$scope.stage.manifestArtifact = artifact;
    });
  };

  public onRequiredArtifactsChanged = (bindings: IManifestBindArtifact[]) => {
    this.$scope.$applyAsync(() => {
      this.$scope.bindings = bindings;
      this.$scope.stage.requiredArtifactIds = bindings.filter((b: IManifestBindArtifact) => b.expectedArtifactId);
      this.$scope.stage.requiredArtifacts = bindings.filter((b: IManifestBindArtifact) => b.artifact);
    });
  };

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

  public checkFeatureFlag(flag: string): boolean {
    return !!SETTINGS.feature[flag];
  }
}
