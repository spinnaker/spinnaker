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
import { ArtifactTypePatterns } from './ArtifactTypes';

type GCEImageArtifactSource = IArtifactSource<IStage | IPipeline>;

const offeredArtifactTypes: RegExp[] = [ArtifactTypePatterns.GCE_MACHINE_IMAGE];

export class NgGCEImageArtifactDelegate implements IExpectedArtifactSelectorViewControllerDelegate {
  private sources: GCEImageArtifactSource[] = [];
  // TODO(sbws): Add UI components for a gce/image expected artifact kind, currently user must define custom.
  private kinds: IArtifactKindConfig[] = Registry.pipeline
    .getArtifactKinds()
    .filter((a: IArtifactKindConfig) => a.key === 'custom');
  // This is always empty for GCE images.
  private accounts: IArtifactAccount[] = [];

  constructor(private $scope: IScope) {
    const { viewState } = $scope.command;
    this.sources = ExpectedArtifactService.sourcesForPipelineStage(viewState.pipeline, viewState.stage);
    this.refreshExpectedArtifacts();
  }

  public getExpectedArtifacts(): IExpectedArtifact[] {
    const { viewState } = this.$scope.command;
    return ExpectedArtifactService.getExpectedArtifactsAvailableToStage(viewState.stage, viewState.pipeline);
  }

  public getSelectedExpectedArtifact(): IExpectedArtifact {
    return (this.getExpectedArtifacts() || []).find(ea => ea.id === this.$scope.command.imageArtifactId);
  }

  public getExpectedArtifactAccounts(): IArtifactAccount[] {
    return this.accounts;
  }

  public getSelectedAccount(): IArtifactAccount {
    return null;
  }

  public getExpectedArtifactSources(): Array<IArtifactSource<any>> {
    return this.sources;
  }

  public getOfferedArtifactTypes(): RegExp[] {
    return offeredArtifactTypes;
  }

  public getSupportedArtifactKinds(): IArtifactKindConfig[] {
    return this.kinds;
  }

  public setSelectedExpectedArtifact(e: IExpectedArtifact): void {
    this.$scope.command.imageArtifactId = e.id;
    this.$scope.gceImageArtifact.showCreateArtifactForm = false;
    this.$scope.$apply();
  }

  public setSelectedArtifactAccount(_a: IArtifactAccount): void {
    return;
  }

  public createArtifact(): void {
    this.$scope.gceImageArtifact.showCreateArtifactForm = true;
    this.$scope.$apply();
  }

  public refreshExpectedArtifacts(): void {
    this.$scope.command.viewState.expectedArtifacts = this.getExpectedArtifacts();
  }
}
