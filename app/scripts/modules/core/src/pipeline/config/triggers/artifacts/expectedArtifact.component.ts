import { equals, IComponentController, IComponentOptions, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { IExpectedArtifact, IPipeline } from 'core/domain';

class ExpectedArtifactController implements IComponentController {
  public expectedArtifact: IExpectedArtifact;
  public pipeline: IPipeline;

  private static expectedArtifactEquals(first: IExpectedArtifact, other: IExpectedArtifact): boolean {
    // Interesting to point out that if two artifact's match artifacts equal, they match the same artifacts & are effectively equal
    return equals(first.matchArtifact, other.matchArtifact);
  }

  public removeExpectedArtifact(): void {
    this.pipeline.expectedArtifacts = this.pipeline.expectedArtifacts
      .filter(a => !ExpectedArtifactController.expectedArtifactEquals(a, this.expectedArtifact));

    this.pipeline.triggers
      .forEach(t => t.expectedArtifacts = t.expectedArtifacts
        .filter(a => !ExpectedArtifactController.expectedArtifactEquals(a, this.expectedArtifact)));
  }
}

class ExpectedArtifactComponent implements IComponentOptions {
  public bindings = { expectedArtifact: '=', pipeline: '=' };
  public controller = ExpectedArtifactController;
  public controllerAs = 'ctrl';
  public template = `
<div class="row">
  <div class="col-md-12">
    <div class="form-horizontal panel-pipeline-phase">
      <div class="form-group row">
        <div class="col-md-3">
          Match against
          <help-field key="pipeline.config.expectedArtifact.matchArtifact"></help-field>
        </div>
        <div class="col-md-2 col-md-offset-7">
        <button class="btn btn-sm btn-default" ng-click="ctrl.removeExpectedArtifact()">
          <span class="glyphicon glyphicon-trash" uib-tooltip="Remove expected artifact"></span>
          <span class="visible-xl-inline">Remove artifact</span>
        </button>
      </div>
    </div>
    <artifact artifact="ctrl.expectedArtifact.matchArtifact"></artifact>
    If missing
    <help-field key="pipeline.config.expectedArtifact.ifMissing"></help-field>
    <div class="form-group row">
      <label class="col-md-3 sm-label-right">
        Use Prior Execution
      </label>
      <input class="col-md-1" type="checkbox" ng-model="ctrl.expectedArtifact.usePriorExecution">
    </div>
    <div class="form-group row">
      <label class="col-md-3 sm-label-right">
        Use Default Artifact
      </label>
      <input class="col-md-1" type="checkbox" ng-model="ctrl.expectedArtifact.useDefaultArtifact">
    </div>
      <div ng-show="ctrl.expectedArtifact.useDefaultArtifact">
        <div class="form-group row">
          <div class="col-md-3">
            Default artifact
            <help-field key="pipeline.config.expectedArtifact.defaultArtifact"></help-field>
          </div>
        </div>
        <div class="form-group row">
          <artifact artifact="ctrl.expectedArtifact.defaultArtifact"></artifact>
        </div>
      </div>
    </div>
  </div>
</div>
`
}

export const EXPECTED_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifacts.expected';
module(EXPECTED_ARTIFACT, [
  PIPELINE_CONFIG_PROVIDER,
]).component('expectedArtifact', new ExpectedArtifactComponent());
