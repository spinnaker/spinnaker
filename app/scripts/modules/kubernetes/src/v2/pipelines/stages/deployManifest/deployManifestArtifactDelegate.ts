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
} from '@spinnaker/core';

type ManifestArtifactSource = IArtifactSource<IStage | IPipeline>;

export class DeployManifestArtifactDelegate implements IExpectedArtifactSelectorViewControllerDelegate {
  private sources: ManifestArtifactSource[];
  private kinds: IArtifactKindConfig[];
  private accounts: IArtifactAccount[];

  constructor(private $scope: IScope, private excludedArtifactTypes: RegExp[]) {
    this.sources = ExpectedArtifactService.sourcesForPipelineStage(this.$scope.$parent.pipeline, this.$scope.stage);
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

  public getExpectedArtifactAccounts = (): IArtifactAccount[] => {
    return this.accounts;
  };

  public getSelectedAccount = (): IArtifactAccount => {
    const accountName = this.$scope.stage.manifestArtifactAccount;
    if (accountName == null) {
      return null;
    }
    return this.getExpectedArtifactAccounts().find(a => a.name === accountName);
  };

  public getExpectedArtifactSources = (): ManifestArtifactSource[] => {
    return this.sources;
  };

  public getSupportedArtifactKinds = (): IArtifactKindConfig[] => {
    return this.kinds;
  };

  public setAccounts = (accounts: IArtifactAccount[]) => {
    this.accounts = [...accounts];
  };

  public setSelectedExpectedArtifact = (expectedArtifact: IExpectedArtifact) => {
    this.$scope.showCreateArtifactForm = false;
    this.$scope.stage.manifestArtifactId = expectedArtifact.id;
    this.$scope.$apply();
  };

  public setSelectedArtifactAccount(account: IArtifactAccount) {
    if (account) {
      this.$scope.stage.manifestArtifactAccount = account.name;
    } else {
      this.$scope.stage.manifestArtifactAccount = '';
    }
    this.$scope.$apply();
  }

  public createArtifact() {
    this.$scope.showCreateArtifactForm = true;
    this.$scope.$apply();
  }

  public refreshExpectedArtifacts() {
    this.$scope.expectedArtifacts = this.getExpectedArtifacts();
  }
}
