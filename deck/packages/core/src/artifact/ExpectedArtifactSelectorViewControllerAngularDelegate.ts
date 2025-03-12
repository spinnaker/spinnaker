import type { IScope } from 'angular';
import type { IArtifactAccount } from '../account';
import type { IArtifactKindConfig } from '../domain';

export abstract class ExpectedArtifactSelectorViewControllerAngularDelegate<ArtifactSource> {
  protected sources: ArtifactSource[] = [];
  protected kinds: IArtifactKindConfig[] = [];
  protected accounts: IArtifactAccount[] = [];

  constructor(protected $scope: IScope) {}

  public getExpectedArtifactSources(): ArtifactSource[] {
    return this.sources;
  }

  public getSupportedArtifactKinds(): IArtifactKindConfig[] {
    return this.kinds;
  }

  public getExpectedArtifactAccounts(): IArtifactAccount[] {
    return this.accounts;
  }

  public setAccounts = (accounts: IArtifactAccount[]) => {
    this.accounts = [...accounts];
  };

  protected scopeApply() {
    this.$scope.$evalAsync();
  }
}
