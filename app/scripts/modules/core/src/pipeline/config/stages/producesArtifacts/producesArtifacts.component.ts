import { IComponentOptions, IController, module } from 'angular';

import { UUIDGenerator } from 'core/utils/uuid.service';
import { IStage, IExpectedArtifact } from 'core';

class ProducesArtifactsCtrl implements IController {
  public stage: IStage;

  constructor() {
    'nginject';
  }

  public hasExpectedArtifacts(): boolean {
    return this.stage
      && this.stage.expectedArtifacts instanceof Array
      && this.stage.expectedArtifacts.length > 0;
  }

  public removeExpectedArtifact(stage: IStage, expectedArtifact: IExpectedArtifact) {
    if (!this.hasExpectedArtifacts()) {
      return;
    }

    stage.expectedArtifacts = stage.expectedArtifacts
      .filter((a: IExpectedArtifact) => a.id !== expectedArtifact.id);
  }

  private defaultArtifact() {
    return { kind: 'custom' };
  }

  public addExpectedArtifact() {
    const newArtifact = {
      matchArtifact: this.defaultArtifact(),
      usePriorExecution: false,
      useDefaultArtifact: false,
      defaultArtifact: this.defaultArtifact(),
      id: UUIDGenerator.generateUuid()
    };

    if (!this.stage.expectedArtifacts) {
      this.stage.expectedArtifacts = [];
    }

    this.stage.expectedArtifacts.push(newArtifact);
  }
}

class ProducesArtifactsComponent implements IComponentOptions {
  public bindings: any = { stage: '=' };
  public controllerAs = 'ctrl';
  public controller = ProducesArtifactsCtrl;
  public template = `
    <div class="container-fluid form-horizontal">
      <expected-artifact
        ng-repeat="expectedArtifact in ctrl.stage.expectedArtifacts"
        remove-expected-artifact="ctrl.removeExpectedArtifact"
        context="ctrl.stage"
        expected-artifact="expectedArtifact"
        application="application">
      </expected-artifact>
      <div class="row" ng-if="ctrl.hasExpectedArtifacts()">
        <p class="col-md-12">
          You don't have any expected artifacts for {{ ctrl.stage.name }}.
        </p>
      </div>
      <div class="row">
        <div class="col-md-12">
          <button class="btn btn-block btn-add-trigger add-new" ng-click="ctrl.addExpectedArtifact()">
            <span class="glyphicon glyphicon-plus-sign"></span> Add Artifact
          </button>
        </div>
      </div>
    </div>
  `;
}

export const PRODUCES_ARTIFACTS = 'spinnaker.core.pipeline.stage.producesArtifacts';
module(PRODUCES_ARTIFACTS, [])
  .component('producesArtifacts', new ProducesArtifactsComponent());
