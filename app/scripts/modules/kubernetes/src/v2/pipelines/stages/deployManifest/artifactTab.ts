import { IComponentOptions, IController, module } from 'angular';
import { get, includes } from 'lodash';
import { ARTIFACT_ICON_LIST } from '@spinnaker/core';

import './artifactTab.less';

class KubernetesExecutionArtifactTabController implements IController {
  private _stage: any;
  private _execution: any;

  public consumedArtifacts: any[] = [];
  public producedArtifacts: any[] = [];

  get stage(): any {
    return this._stage;
  }

  set stage(stage: any) {
    this._stage = stage;
    this.populateArtifactLists();
  }

  get execution(): any {
    return this._execution;
  }

  set execution(execution: any) {
    this._execution = execution;
    this.populateArtifactLists();
  }

  public populateArtifactLists() {
    const requiredArtifactIds = get(this.stage, ['context', 'requiredArtifactIds'], []);
    const manifestArtifactId = get(this.stage, ['context', 'manifestArtifactId'], null);
    const resolvedExpectedArtifacts = get(this.execution, ['trigger', 'resolvedExpectedArtifacts'], []);

    const consumedIds = requiredArtifactIds.slice();
    if (manifestArtifactId) {
      consumedIds.push(manifestArtifactId);
    }

    this.consumedArtifacts = resolvedExpectedArtifacts
      .filter(rea => includes(consumedIds, rea.id))
      .map(rea => rea.boundArtifact)
      .filter(({ name, type }) => name && type);

    this.producedArtifacts = get(this.stage, ['outputs', 'artifacts'], []).slice();
  }
}

class KubernetesExecutionArtifactTabComponent implements IComponentOptions {
  public bindings: any = { stage: '<', execution: '<' };
  public controller: any = KubernetesExecutionArtifactTabController;
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

export const KUBERNETES_EXECUTION_ARTIFACT_TAB =
  'spinnaker.kubernetes.v2.kubernetes.deployManifest.artifactTab.component';

module(KUBERNETES_EXECUTION_ARTIFACT_TAB, [ARTIFACT_ICON_LIST]).component(
  'kubernetesExecutionArtifactTab',
  new KubernetesExecutionArtifactTabComponent(),
);
