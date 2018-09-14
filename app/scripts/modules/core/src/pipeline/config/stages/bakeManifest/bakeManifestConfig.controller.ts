import { IController, IScope } from 'angular';

import {
  AccountService,
  ExpectedArtifactSelectorViewController,
  NgBakeManifestArtifactDelegate,
  IArtifactAccount,
} from 'core';
import { UUIDGenerator } from 'core/utils';

export interface IInputArtifact {
  id: string;
  account: string;
}

export class BakeManifestConfigCtrl implements IController {
  public artifactControllers: any[];
  public artifactAccounts: IArtifactAccount[] = [];
  public templateRenderers = ['HELM2'];

  public defaultInputArtifact(artifact = { id: '', account: '' }): InputArtifact {
    const inputArtifact = new InputArtifact(this.$scope, artifact);
    inputArtifact.delegate.setAccounts(this.artifactAccounts);
    inputArtifact.controller.updateAccounts(inputArtifact.delegate.getSelectedExpectedArtifact());
    return inputArtifact;
  }

  constructor(public $scope: IScope) {
    'ngInject';
    const { stage } = this.$scope;
    if (stage.isNew) {
      const defaultSelection = {
        templateRenderer: 'HELM2',
        expectedArtifacts: [
          {
            matchArtifact: {
              type: 'embedded/base64',
              kind: 'base64',
              name: '',
            },
            id: UUIDGenerator.generateUuid(),
            defaultArtifact: {},
            useDefaultArtifact: false,
          },
        ],
        inputArtifacts: [this.defaultInputArtifact()],
      };

      Object.assign(stage, defaultSelection);
    }
    this.ensureTemplateArtifact();
    stage.inputArtifacts = stage.inputArtifacts.map((a: IInputArtifact) => this.defaultInputArtifact(a));
    AccountService.getArtifactAccounts().then(accounts => {
      this.artifactAccounts = accounts;
      stage.inputArtifacts.forEach((a: InputArtifact) => {
        a.delegate.setAccounts(accounts);
        a.controller.updateAccounts(a.delegate.getSelectedExpectedArtifact());
      });
    });
  }

  public hasValueArtifacts(): boolean {
    this.ensureTemplateArtifact();
    return this.$scope.stage.inputArtifacts.length > 1;
  }

  public addInputArtifact() {
    this.ensureTemplateArtifact();
    this.$scope.stage.inputArtifacts.push(this.defaultInputArtifact());
  }

  public removeInputArtifact(i: number) {
    this.ensureTemplateArtifact();
    if (i <= 0) {
      return;
    }
    this.$scope.stage.inputArtifacts.splice(i, 1);
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

  public templateArtifact() {
    this.ensureTemplateArtifact();
    return this.$scope.stage.inputArtifacts[0];
  }

  public ensureTemplateArtifact() {
    // First artifact is special -- the UI depends on it existing. If someone edited the json to remove it,
    // this at least fixes the UI.
    if (!this.$scope.stage.inputArtifacts || this.$scope.stage.inputArtifacts.length === 0) {
      this.$scope.stage.inputArtifacts = [this.defaultInputArtifact()];
    }
  }

  public canShowAccountSelect(artifact: InputArtifact): boolean {
    return (
      artifact &&
      !artifact.delegate.requestingNew &&
      (artifact.controller.accountsForArtifact.length > 1 && artifact.delegate.getSelectedExpectedArtifact() != null)
    );
  }
}

class InputArtifact {
  public controller: ExpectedArtifactSelectorViewController;
  public delegate: NgBakeManifestArtifactDelegate;
  public id: string;
  public account: string;

  constructor(public $scope: IScope, artifact = { id: '', account: '' }) {
    setUnserializable(this, '$scope', $scope);
    setUnserializable(this, 'delegate', new NgBakeManifestArtifactDelegate(this));
    setUnserializable(this, 'controller', new ExpectedArtifactSelectorViewController(this.delegate));
    this.id = artifact.id;
    this.account = artifact.account;
  }
}

const setUnserializable = (obj: any, key: string, value: any) => {
  return Object.defineProperty(obj, key, { configurable: false, enumerable: false, writable: false, value });
};
