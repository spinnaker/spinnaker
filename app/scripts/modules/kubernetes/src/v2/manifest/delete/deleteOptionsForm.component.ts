import { IComponentOptions, IController, module } from 'angular';

import { IDeleteOptions } from './delete.controller';

class KubernetesDeleteManifestOptionsFormCtrl implements IController {
  public options: IDeleteOptions;
}

const kubernetesDeletManifestOptionsFormComponent: IComponentOptions = {
  bindings: { options: '=' },
  controller: KubernetesDeleteManifestOptionsFormCtrl,
  controllerAs: 'ctrl',
  template: `
    <div class="form-horizontal">
      <div class="form-group form-inline">
        <div class="col-md-3 sm-label-right">
          Cascading
          <help-field key="kubernetes.manifest.delete.cascading"></help-field>
        </div>
        <div class="col-md-3">
          <div class="input-group">
            <input type="checkbox"
                  ng-model="ctrl.options.cascading"/>
          </div>
        </div>
      </div>
      <div class="form-group form-inline">
        <div class="col-md-3 sm-label-right">
          Grace Period
          <help-field key="kubernetes.manifest.delete.gracePeriod"></help-field>
        </div>
        <div class="col-md-4">
          <div class="input-group">
            <input type="number"
                  class="form-control input-sm highlight-pristine"
                  ng-model="ctrl.options.gracePeriodSeconds"
                  min="0"/>
            <span class="input-group-addon">second<span ng-if="ctrl.options.gracePeriodSeconds !== 1">s</span></span>
          </div>
        </div>
      </div>
    </div>
  `,
};

export const KUBERNETES_DELETE_MANIFEST_OPTIONS_FORM =
  'spinnaker.kubernetes.v2.kubernetes.manifest.delete.options.component';
module(KUBERNETES_DELETE_MANIFEST_OPTIONS_FORM, []).component(
  'kubernetesDeleteManifestOptionsForm',
  kubernetesDeletManifestOptionsFormComponent,
);
