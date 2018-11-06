import { IScope } from 'angular';
import { get } from 'lodash';
import {
  ExpectedArtifactService,
  Registry,
  IExpectedArtifact,
  IArtifactAccount,
  IArtifactSource,
  IExpectedArtifactSelectorViewControllerDelegate,
  IStage,
  IPipeline,
} from 'core';
import { ExpectedArtifactSelectorViewControllerAngularDelegate } from './ExpectedArtifactSelectorViewControllerAngularDelegate';

export class NgAppengineConfigArtifactDelegate
  extends ExpectedArtifactSelectorViewControllerAngularDelegate<IArtifactSource<IStage | IPipeline>>
  implements IExpectedArtifactSelectorViewControllerDelegate {
  public expectedArtifacts: IExpectedArtifact[];
  public requestingNew = false;

  private getStage() {
    return get(this, 'artifact.$scope.command.viewState.stage', null);
  }

  private getPipeline() {
    return get(this, 'artifact.$scope.command.viewState.pipeline', null);
  }

  constructor(private artifact: { $scope: IScope; id: string; account: string }) {
    super(artifact.$scope);
    this.refreshExpectedArtifacts();
    this.sources = ExpectedArtifactService.sourcesForPipelineStage(() => this.getPipeline(), this.getStage());
    this.kinds = Registry.pipeline.getArtifactKinds().filter(a => a.isMatch);
  }

  public setAccounts = (accounts: any) => {
    this.accounts = [...accounts];
  };

  public getExpectedArtifacts(): IExpectedArtifact[] {
    return ExpectedArtifactService.getExpectedArtifactsAvailableToStage(this.getStage(), this.getPipeline());
  }

  public getSelectedExpectedArtifact(): IExpectedArtifact {
    return this.expectedArtifacts.find(ea => ea.id === this.artifact.id);
  }

  public getSelectedAccount(): IArtifactAccount {
    return this.accounts.find(a => a.name === this.artifact.account);
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
