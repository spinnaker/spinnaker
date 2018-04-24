import { IController, IScope } from 'angular';

import { AccountService, ExpectedArtifactService, IAccount, IExpectedArtifact } from 'core';

export class BakeManifestConfigCtrl implements IController {
  public expectedArtifacts: IExpectedArtifact[];
  public artifactAccounts: IAccount[];
  public templateRenderers = ['HELM2'];

  public static defaultInputArtifact(): any {
    return {
      id: '',
      account: ''
    }
  }

  constructor(public $scope: IScope, expectedArtifactService: ExpectedArtifactService ) {
    'ngInject';
    if (this.$scope.stage.isNew) {
      const defaultSelection = {
        templateRenderer: 'HELM2',
        expectedArtifacts: [{
          matchArtifact: {
            type: 'embedded/base64',
            name: ''
          }
        }],
        inputArtifacts: [  BakeManifestConfigCtrl.defaultInputArtifact() ]
      };

      Object.assign(this.$scope.stage, defaultSelection);
    }

    AccountService.getArtifactAccounts().then(accounts => {
      this.artifactAccounts = accounts;
    });

    this.expectedArtifacts = expectedArtifactService.getExpectedArtifactsAvailableToStage(
      this.$scope.stage,
      this.$scope.$parent.pipeline,
    );
  }

  public addInputArtifact() {
    // First artifact is special -- the UI depends on it existing. If someone edited the json to remove it,
    // this at least fixes the UI.
    if (!this.$scope.stage.inputArtifacts) {
      this.$scope.stage.inputArtifacts = [ BakeManifestConfigCtrl.defaultInputArtifact() ];
    }

    this.$scope.stage.inputArtifacts.push(BakeManifestConfigCtrl.defaultInputArtifact())
  }

  public removeInputArtifact(i: number) {
    if (!this.$scope.stage.inputArtifacts) {
      this.$scope.stage.inputArtifacts = [ BakeManifestConfigCtrl.defaultInputArtifact() ];
    }

    if (i <= 0) {
      return;
    }

    this.$scope.stage.inputArtifacts.splice(i, 1)
  }

  public outputNameChange() {
    const expectedArtifacts = this.$scope.stage.expectedArtifacts;
    if (
      expectedArtifacts &&
      expectedArtifacts.length === 1 &&
      expectedArtifacts[0].matchArtifact &&
      expectedArtifacts[0].matchArtifact.type === 'embedded/base64'
    ) {
      expectedArtifacts[0].matchArtifact.name = this.$scope.stage.outputName;
    }
  }
}
