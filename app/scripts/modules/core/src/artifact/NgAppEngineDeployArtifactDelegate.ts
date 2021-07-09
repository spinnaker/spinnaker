import { IScope } from 'angular';

import { IExpectedArtifactSelectorViewControllerDelegate } from './ExpectedArtifactSelectorViewController';
import { ExpectedArtifactSelectorViewControllerAngularDelegate } from './ExpectedArtifactSelectorViewControllerAngularDelegate';
import { IArtifactAccount } from '../account';
import { IArtifactKindConfig, IArtifactSource, IExpectedArtifact, IPipeline, IStage } from '../domain';
import { ExpectedArtifactService } from './expectedArtifact.service';
import { Registry } from '../registry';

export class NgAppEngineDeployArtifactDelegate
  extends ExpectedArtifactSelectorViewControllerAngularDelegate<IArtifactSource<IStage | IPipeline>>
  implements IExpectedArtifactSelectorViewControllerDelegate {
  constructor(protected $scope: IScope, protected offeredArtifactTypes: RegExp[] = null) {
    super($scope);
    const { viewState } = $scope.command;
    this.sources = ExpectedArtifactService.sourcesForPipelineStage(() => viewState.pipeline, viewState.stage);
    this.kinds = Registry.pipeline.getMatchArtifactKinds().filter((a: IArtifactKindConfig) => {
      return a.customKind || offeredArtifactTypes.find((oat) => oat.test(a.type));
    });
    this.refreshExpectedArtifacts();
  }

  public getExpectedArtifacts(): IExpectedArtifact[] {
    const { viewState } = this.$scope.command;
    return ExpectedArtifactService.getExpectedArtifactsAvailableToStage(viewState.stage, viewState.pipeline);
  }

  public getSelectedExpectedArtifact(): IExpectedArtifact {
    return (this.getExpectedArtifacts() || []).find((ea) => ea.id === this.$scope.command.expectedArtifactId);
  }

  public getSelectedAccount(): IArtifactAccount {
    return null;
  }

  public getOfferedArtifactTypes(): RegExp[] {
    return this.offeredArtifactTypes;
  }

  public setSelectedExpectedArtifact(e: IExpectedArtifact): void {
    this.$scope.command.expectedArtifactId = e.id;
    this.$scope.showCreateArtifactForm = false;
    this.scopeApply();
  }

  public setSelectedArtifactAccount(_a: IArtifactAccount): void {}

  public createArtifact(): void {
    this.$scope.showCreateArtifactForm = true;
    this.scopeApply();
  }

  public refreshExpectedArtifacts(): void {
    this.$scope.command.backingData.expectedArtifacts = this.getExpectedArtifacts();
  }
}
