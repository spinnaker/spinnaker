import { IScope } from 'angular';

import { IExpectedArtifactSelectorViewControllerDelegate } from './ExpectedArtifactSelectorViewController';
import { ExpectedArtifactSelectorViewControllerAngularDelegate } from './ExpectedArtifactSelectorViewControllerAngularDelegate';
import { IArtifactAccount } from '../account';
import { IArtifactSource, IExpectedArtifact, IPipeline, IStage } from '../domain';
import { ExpectedArtifactService } from './expectedArtifact.service';
import { Registry } from '../registry';

export class NgBakeManifestArtifactDelegate
  extends ExpectedArtifactSelectorViewControllerAngularDelegate<IArtifactSource<IStage | IPipeline>>
  implements IExpectedArtifactSelectorViewControllerDelegate {
  public expectedArtifacts: IExpectedArtifact[];
  public requestingNew = false;

  constructor(private artifact: { $scope: IScope; id: string; account: string }) {
    super(artifact.$scope);
    this.refreshExpectedArtifacts();
    this.sources = ExpectedArtifactService.sourcesForPipelineStage(
      () => this.$scope.$parent.pipeline,
      this.$scope.stage,
    );
    this.kinds = Registry.pipeline.getMatchArtifactKinds();
  }

  public setAccounts = (accounts: any) => {
    this.accounts = [...accounts];
  };

  public getExpectedArtifacts(): IExpectedArtifact[] {
    return ExpectedArtifactService.getExpectedArtifactsAvailableToStage(
      this.$scope.stage,
      this.$scope.$parent.pipeline,
    );
  }

  public getSelectedExpectedArtifact(): IExpectedArtifact {
    return this.expectedArtifacts.find((ea) => ea.id === this.artifact.id);
  }

  public getSelectedAccount(): IArtifactAccount {
    return this.accounts.find((a) => a.name === this.artifact.account);
  }

  public setSelectedExpectedArtifact(e: IExpectedArtifact): void {
    this.artifact.id = e.id;
    this.requestingNew = false;
    this.scopeApply();
  }

  public setSelectedArtifactAccount(a: IArtifactAccount): void {
    if (a == null) {
      this.artifact.account = '';
    } else {
      this.artifact.account = a.name;
    }
    this.scopeApply();
  }

  public createArtifact(): void {
    this.requestingNew = true;
    this.scopeApply();
  }

  public refreshExpectedArtifacts(): void {
    this.expectedArtifacts = this.getExpectedArtifacts();
  }
}
