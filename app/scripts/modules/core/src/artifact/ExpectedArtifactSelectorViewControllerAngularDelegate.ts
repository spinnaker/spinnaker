import { IScope } from 'angular';
import { IArtifactAccount, IArtifactKindConfig } from 'core';

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
}
