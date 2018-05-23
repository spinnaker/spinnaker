import { IComponentOptions, IController, module } from 'angular';
import { has, get, includes } from 'lodash';
import { ARTIFACT_ICON_LIST } from './artifactIconList';
import { IArtifact, IExpectedArtifact, IExecution, IStage } from 'core/domain';

import './artifactTab.less';

class ExecutionArtifactTabController implements IController {
  private _stage: IStage;
  private _execution: IExecution;
  private _artifactFields: String[];

  public consumedArtifacts: IArtifact[] = [];
  public producedArtifacts: IArtifact[] = [];

  get stage(): IStage {
    return this._stage;
  }

  set stage(stage: IStage) {
    this._stage = stage;
    this.populateArtifactLists();
  }

  get execution(): IExecution {
    return this._execution;
  }

  set execution(execution: IExecution) {
    this._execution = execution;
    this.populateArtifactLists();
  }

  get artifactFields(): String[] {
    return this._artifactFields;
  }

  set artifactFields(artifactFields: String[]) {
    this._artifactFields = artifactFields || [];
    this.populateArtifactLists();
  }

  private extractBoundArtifactsFromExecution(execution: IExecution): IExpectedArtifact[] {
    const triggerArtifacts = get(execution, ['trigger', 'resolvedExpectedArtifacts'], []);
    const stageOutputArtifacts = get(execution, 'stages', []).reduce((out, stage) => {
      const outputArtifacts = get(stage, ['outputs', 'resolvedExpectedArtifacts'], []);
      return out.concat(outputArtifacts);
    }, []);
    const allArtifacts = triggerArtifacts.concat(stageOutputArtifacts);
    return allArtifacts.filter(a => has(a, 'boundArtifact'));
  }

  public populateArtifactLists() {
    const accumulateArtifacts = (artifacts: String[], field: String) => {
      // fieldValue will be either a string with a single artifact id, or an array of artifact ids
      // In either case, concatenate the value(s) onto the array of artifacts; the one exception
      // is that we don't want to include an empty string in the artifact list, so concatenate
      // an empty array (ie, no-op) if fieldValue is falsey.
      const fieldValue: String | String[] = get(this.stage, ['context', field], []);
      return artifacts.concat(fieldValue || []);
    };

    const consumedIds = this.artifactFields.reduce(accumulateArtifacts, []);
    const boundArtifacts = this.extractBoundArtifactsFromExecution(this.execution);

    this.consumedArtifacts = boundArtifacts
      .filter(rea => includes(consumedIds, rea.id))
      .map(rea => rea.boundArtifact)
      .filter(({ name, type }) => name && type);

    this.producedArtifacts = get(this.stage, ['outputs', 'artifacts'], []).slice();
  }
}

class ExecutionArtifactTabComponent implements IComponentOptions {
  public bindings: any = { artifactFields: '<', execution: '<', stage: '<' };
  public controller: any = ExecutionArtifactTabController;
  public controllerAs = 'ctrl';
  public template = `
<div class="row execution-artifacts">
  <div class="col-md-6 artifact-list-container">
    <h5>Consumed Artifacts</h5>
    <div ng-if="ctrl.consumedArtifacts.length">
      <artifact-icon-list artifacts="ctrl.consumedArtifacts">
    </div>
  </div>
  <div class="col-md-6 artifact-list-container">
    <h5>Produced Artifacts</h5>
    <div ng-if="ctrl.producedArtifacts.length">
      <artifact-icon-list artifacts="ctrl.producedArtifacts">
    </div>
  </div>
</div>
`;
}

export const EXECUTION_ARTIFACT_TAB = 'spinnaker.core.artifact.artifactTab.component';

module(EXECUTION_ARTIFACT_TAB, [ARTIFACT_ICON_LIST]).component(
  'executionArtifactTab',
  new ExecutionArtifactTabComponent(),
);
