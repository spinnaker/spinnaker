import { IController, IScope } from 'angular';

import { AccountService, IArtifactAccount } from 'core/account';
import {
  ExpectedArtifactSelectorViewController,
  NgBakeManifestArtifactDelegate,
  IArtifactAccountPair,
} from 'core/artifact';
import { UUIDGenerator } from 'core/utils';

class InputArtifact implements IArtifactAccountPair {
  public $scope: IScope;
  public controller: ExpectedArtifactSelectorViewController;
  public delegate: NgBakeManifestArtifactDelegate;
  public id: string;
  public account: string;

  constructor($scope: IScope, artifact = { id: '', account: '' }) {
    const unserializable = { configurable: false, enumerable: false, writable: false };
    Object.defineProperty(this, '$scope', { ...unserializable, value: $scope });
    const delegate = new NgBakeManifestArtifactDelegate(this);
    const controller = new ExpectedArtifactSelectorViewController(delegate);
    Object.defineProperty(this, 'delegate', { ...unserializable, value: delegate });
    Object.defineProperty(this, 'controller', { ...unserializable, value: controller });
    this.id = artifact.id;
    this.account = artifact.account;
  }
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

  public static $inject = ['$scope'];
  constructor(public $scope: IScope) {
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
        evaluateOverrideExpressions: false,
      };

      Object.assign(stage, defaultSelection);
    }
    this.ensureTemplateArtifact();
    AccountService.getArtifactAccounts().then(accounts => {
      this.artifactAccounts = accounts;
      stage.inputArtifacts = stage.inputArtifacts.map((a: IArtifactAccountPair) => this.defaultInputArtifact(a));
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
      artifact.delegate &&
      artifact.controller &&
      !artifact.delegate.requestingNew &&
      (artifact.controller.accountsForArtifact.length > 1 && artifact.delegate.getSelectedExpectedArtifact() != null)
    );
  }
}
