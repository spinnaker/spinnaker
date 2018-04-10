import { IController, IScope } from 'angular';

import { AccountService, ExpectedArtifactService, IAccount, IExpectedArtifact } from 'core';

export class BakeManifestConfigCtrl implements IController {
  public expectedArtifacts: IExpectedArtifact[];
  public artifactAccounts: IAccount[];
  public templateRenderers = ['HELM2'];

  constructor(public $scope: IScope,
              accountService: AccountService,
              expectedArtifactService: ExpectedArtifactService ) {
    'ngInject';
    if (this.$scope.stage.isNew) {
      const defaultSelection = {
        templateRenderer: 'HELM2',
        expectedArtifacts: [{
          matchArtifact: {
            type: 'embedded/base64',
            name: ''
          }
        }]
      };

      Object.assign(this.$scope.stage, defaultSelection);
    }

    accountService.getArtifactAccounts().then(accounts => {
      this.artifactAccounts = accounts;
    });

    this.expectedArtifacts = expectedArtifactService.getExpectedArtifactsAvailableToStage(this.$scope.stage, this.$scope.$parent.pipeline);
  }

  public outputNameChange() {
    const expectedArtifacts = this.$scope.stage.expectedArtifacts;
    if (expectedArtifacts
      && expectedArtifacts.length === 1
      && expectedArtifacts[0].matchArtifact
      && expectedArtifacts[0].matchArtifact.type === 'embedded/base64') {
      expectedArtifacts[0].matchArtifact.name = this.$scope.stage.outputName;
    }
  }
}
