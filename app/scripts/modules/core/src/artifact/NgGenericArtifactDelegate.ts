import { IScope } from 'angular';

import { IArtifactKindConfig, IExpectedArtifact, IArtifactSource, IStage, IPipeline } from 'core/domain';
import { ExpectedArtifactService, IExpectedArtifactSelectorViewControllerDelegate } from 'core/artifact';
import { Registry } from 'core/registry';
import { IArtifactAccount } from 'core/account';

import { ExpectedArtifactSelectorViewControllerAngularDelegate } from './ExpectedArtifactSelectorViewControllerAngularDelegate';
import { ArtifactTypePatterns } from './ArtifactTypes';

const defaultExcludedArtifactTypes = [ArtifactTypePatterns.KUBERNETES, ArtifactTypePatterns.DOCKER_IMAGE];

export class NgGenericArtifactDelegate
  extends ExpectedArtifactSelectorViewControllerAngularDelegate<IArtifactSource<IStage | IPipeline>>
  implements IExpectedArtifactSelectorViewControllerDelegate {
  constructor(
    protected $scope: IScope,
    private tag: string,
    private excludedArtifactTypes = defaultExcludedArtifactTypes,
  ) {
    super($scope);
    this.sources = ExpectedArtifactService.sourcesForPipelineStage(
      () => this.$scope.$parent.pipeline,
      this.$scope.stage,
    );
    this.kinds = Registry.pipeline
      .getMatchArtifactKinds()
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
    const id = this.$scope.stage[`${this.tag}ArtifactId`];
    if (id == null) {
      return null;
    }
    return this.getExpectedArtifacts().find(ea => ea.id === id);
  };

  public getSelectedAccount = (): IArtifactAccount => {
    const accountName = this.$scope.stage[`${this.tag}ArtifactAccount`];
    if (accountName == null) {
      return null;
    }
    return this.getExpectedArtifactAccounts().find(a => a.name === accountName);
  };

  public setSelectedExpectedArtifact = (expectedArtifact: IExpectedArtifact) => {
    this.$scope.showCreateArtifactForm = false;
    this.$scope.stage[`${this.tag}ArtifactId`] = expectedArtifact.id;
    this.scopeApply();
  };

  public setSelectedArtifactAccount(account: IArtifactAccount) {
    if (account) {
      this.$scope.stage[`${this.tag}ArtifactAccount`] = account.name;
    } else {
      this.$scope.stage[`${this.tag}ArtifactAccount`] = '';
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
