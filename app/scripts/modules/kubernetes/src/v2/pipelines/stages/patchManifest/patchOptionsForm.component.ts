import { IComponentOptions, IController, module } from 'angular';

export interface IPatchOptions {
  mergeStrategy: MergeStrategy;
  record: boolean;
}

export enum MergeStrategy {
  strategic = 'strategic',
  json = 'json',
  merge = 'merge',
}

class KubernetesPatchManifestOptionsFormCtrl implements IController {
  public options: IPatchOptions;
  public mergeStrategies = MergeStrategy;
}

class KubernetesPatchManifestOptionsFormComponent implements IComponentOptions {
  public bindings: any = { options: '=', onChange: '<' };
  public controller: any = KubernetesPatchManifestOptionsFormCtrl;
  public controllerAs = 'ctrl';

  public template = `
    <div class="form-horizontal">
      <div class="form-group form-inline">
        <div class="col-md-3 sm-label-right">
          Record Patch Annotation
          <help-field key="kubernetes.manifest.patch.record"></help-field>
        </div>
        <div class="col-md-3">
          <div class="input-group">
            <input type="checkbox" ng-model="ctrl.options.record"/>
          </div>
        </div>
      </div>

      <div class="form-group form-inline">
        <div class="col-md-3 sm-label-right">
          Merge Strategy
          <help-field key="kubernetes.manifest.patch.mergeStrategy"></help-field>
        </div>
        <div class="col-md-4">
          <div class="input-group">
            <select class="form-control input-sm"
                    ng-model="ctrl.options.mergeStrategy"
                    ng-change="ctrl.onChange()">
              <option ng-repeat="strategy in ctrl.mergeStrategies" value="{{strategy}}"
                      ng-selected="ctrl.options.mergeStrategy === strategy">
                {{strategy}}
              </option>
            </select>
          </div>
        </div>
      </div>
    </div>
  `;
}

export const KUBERNETES_PATCH_MANIFEST_OPTIONS_FORM = 'spinnaker.kubernetes.v2.manifest.patch.options.component';
module(KUBERNETES_PATCH_MANIFEST_OPTIONS_FORM, []).component(
  'kubernetesPatchManifestOptionsForm',
  new KubernetesPatchManifestOptionsFormComponent(),
);
