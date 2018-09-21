import { IScope } from 'angular';
import {
  ExpectedArtifactService,
  Registry,
  IExpectedArtifact,
  IArtifactAccount,
  IArtifactKindConfig,
  IArtifactSource,
  IExpectedArtifactSelectorViewControllerDelegate,
  IStage,
  IPipeline,
} from 'core';
import { ExpectedArtifactSelectorViewControllerAngularDelegate } from './ExpectedArtifactSelectorViewControllerAngularDelegate';
import { ArtifactTypePatterns } from './ArtifactTypes';

const defaultExcludedArtifactTypes = [ArtifactTypePatterns.KUBERNETES, ArtifactTypePatterns.DOCKER_IMAGE];

export class NgManifestArtifactDelegate
  extends ExpectedArtifactSelectorViewControllerAngularDelegate<IArtifactSource<IStage | IPipeline>>
  implements IExpectedArtifactSelectorViewControllerDelegate {
  constructor(protected $scope: IScope, private excludedArtifactTypes = defaultExcludedArtifactTypes) {
    super($scope);
    this.sources = ExpectedArtifactService.sourcesForPipelineStage(
      () => this.$scope.$parent.pipeline,
      this.$scope.stage,
    );
    this.kinds = Registry.pipeline
      .getArtifactKinds()
      .filter((a: IArtifactKindConfig) => a.isMatch)
      .filter((a: IArtifactKindConfig) => !this.getExcludedArtifactTypes().find(t => t.test(a.type)));
    this.refreshExpectedArtifacts();
  }

  public getExcludedArtifactTypes = (): RegExp[] => {
    return this.excludedArtifactTypes;
  };

  public getExpectedArtifacts = (): IExpectedArtifact[] => {
    return ExpectedArtifactService.getExpectedArtifactsAvailableToStage(
      this.$scope.stage,
      this.$scope.$parent.pipeline,
    );
  };

  public getSelectedExpectedArtifact = (): IExpectedArtifact => {
    const id = this.$scope.stage.manifestArtifactId;
    if (id == null) {
      return null;
    }
    return this.getExpectedArtifacts().find(ea => ea.id === id);
  };

  public getSelectedAccount = (): IArtifactAccount => {
    const accountName = this.$scope.stage.manifestArtifactAccount;
    if (accountName == null) {
      return null;
    }
    return this.getExpectedArtifactAccounts().find(a => a.name === accountName);
  };

  public setSelectedExpectedArtifact = (expectedArtifact: IExpectedArtifact) => {
    this.$scope.showCreateArtifactForm = false;
    this.$scope.stage.manifestArtifactId = expectedArtifact.id;
    this.scopeApply();
  };

  public setSelectedArtifactAccount(account: IArtifactAccount) {
    if (account) {
      this.$scope.stage.manifestArtifactAccount = account.name;
    } else {
      this.$scope.stage.manifestArtifactAccount = '';
    }
    this.scopeApply();
  }

  public createArtifact() {
    this.$scope.showCreateArtifactForm = true;
    this.scopeApply();
  }

  public refreshExpectedArtifacts() {
    this.$scope.expectedArtifacts = this.getExpectedArtifacts();
  }
}
