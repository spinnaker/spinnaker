import { IComponentOptions, IController, module } from 'angular';

class KubernetesManifestConditionCtrl implements IController {
  public condition: any;
}

class KubernetesManifestConditionComponent implements IComponentOptions {
  public bindings: any = { condition: '<' };
  public controller: any = KubernetesManifestConditionCtrl;
  public controllerAs = 'ctrl';
  public template = `
      <span>
        <span ng-if="ctrl.condition.status === 'True'" class="glyphicon glyphicon-Normal"></span>
        <span ng-if="ctrl.condition.status === 'False'" class="glyphicon glyphicon-Warn"></span>
        <span ng-if="ctrl.condition.status === 'Unknown'"> ? </span>
        <b>{{ctrl.condition.type}}</b>
        <i>{{ctrl.condition.lastTransitionTime}}</i>
      </span>
      <div>
        {{ctrl.condition.message}}
      </div>
  `;
}

export const KUBERNETES_MANIFEST_CONDITION = 'spinnaker.kubernetes.v2.manifest.condition.component';
module(KUBERNETES_MANIFEST_CONDITION, [])
  .component('kubernetesManifestCondition', new KubernetesManifestConditionComponent());
