import { IScope } from 'angular';

import { ArtifactTypePatterns } from './ArtifactTypes';
import { IExpectedArtifactSelectorViewControllerDelegate } from './ExpectedArtifactSelectorViewController';
import { ExpectedArtifactSelectorViewControllerAngularDelegate } from './ExpectedArtifactSelectorViewControllerAngularDelegate';
import { IArtifactAccount } from '../account';
import { IArtifactKindConfig, IArtifactSource, IExpectedArtifact, IPipeline, IStage } from '../domain';
import { ExpectedArtifactService } from './expectedArtifact.service';
import { Registry } from '../registry';

const offeredArtifactTypes: RegExp[] = [ArtifactTypePatterns.GCE_MACHINE_IMAGE];

export class NgGCEImageArtifactDelegate
  extends ExpectedArtifactSelectorViewControllerAngularDelegate<IArtifactSource<IStage | IPipeline>>
  implements IExpectedArtifactSelectorViewControllerDelegate {
  // TODO(sbws): Add UI components for a gce/image expected artifact kind, currently user must define custom.
  protected kinds: IArtifactKindConfig[] = [Registry.pipeline.getCustomArtifactKind()];

  constructor(protected $scope: IScope) {
    super($scope);
    const { viewState } = $scope.command;
    this.sources = ExpectedArtifactService.sourcesForPipelineStage(() => viewState.pipeline, viewState.stage);
    this.refreshExpectedArtifacts();
  }

  public getExpectedArtifacts(): IExpectedArtifact[] {
    const { viewState } = this.$scope.command;
    return ExpectedArtifactService.getExpectedArtifactsAvailableToStage(viewState.stage, viewState.pipeline);
  }

  public getSelectedExpectedArtifact(): IExpectedArtifact {
    return (this.getExpectedArtifacts() || []).find((ea) => ea.id === this.$scope.command.imageArtifactId);
  }

  public getSelectedAccount(): IArtifactAccount {
    return null;
  }

  public getOfferedArtifactTypes(): RegExp[] {
    return offeredArtifactTypes;
  }

  public setSelectedExpectedArtifact(e: IExpectedArtifact): void {
    this.$scope.command.imageArtifactId = e.id;
    this.$scope.gceImageArtifact.showCreateArtifactForm = false;
    this.scopeApply();
  }

  public setSelectedArtifactAccount(_a: IArtifactAccount): void {
    return;
  }

  public createArtifact(): void {
    this.$scope.gceImageArtifact.showCreateArtifactForm = true;
    this.scopeApply();
  }

  public refreshExpectedArtifacts(): void {
    this.$scope.command.viewState.expectedArtifacts = this.getExpectedArtifacts();
  }
}
