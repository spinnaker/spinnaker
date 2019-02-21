import { IComponentOptions, IController, module } from 'angular';

import { IManifestLabelSelector, LABEL_KINDS } from '../IManifestLabelSelector';

class KubernetesManifestLabelEditorCtrl implements IController {
  public selectors: IManifestLabelSelector[];
  public valueMetadata: string[] = []; // the index of 'selectors' and 'valueMetadata' align.
  public kinds: string[] = LABEL_KINDS;

  public $onInit() {
    this.selectors.forEach(e => {
      this.valueMetadata.push(e.values.join(', '));
    });
  }

  public addField(): void {
    this.selectors.push({ key: '', kind: 'EQUALS', values: [] });
    this.valueMetadata.push('');
  }

  public removeField(index: number): void {
    this.selectors.splice(index, 1);
    this.valueMetadata.splice(index, 1);
  }

  public convertValueStringToArray(index: number): void {
    this.selectors[index].values = this.valueMetadata[index].split(',').map(e => e.trim());
  }
}

const kubernetesManifestLabelEditorComponent: IComponentOptions = {
  bindings: { selectors: '=' },
  controller: KubernetesManifestLabelEditorCtrl,
  controllerAs: 'ctrl',
  template: `
    <form name="labelEditor">
      <table class="table table-condensed packed tags">
        <thead>
        <tr>
          <th>Key</th>
          <th>Kind</th>
          <th>Value(s)</th>
          <th></th>
        </tr>
        </thead>
        <tbody>
        <tr ng-repeat="selector in ctrl.selectors">
          <td>
            <input class="form-control input input-sm" type="text"
                   name="{{$index}}"
                   ng-model="selector.key">
          </td>
          <td>
            <select class="form-control input input-sm" ng-model="selector.kind" ng-options="v for v in ctrl.kinds">
            </select>
          </td>
          <td>
            <input class="form-control input input-sm" type="text" placeholder="Comma seperated values"
                   ng-model="ctrl.valueMetadata[$index]" ng-change="ctrl.convertValueStringToArray($index)">
          </td>
          <td>
            <div class="form-control-static">
              <a href ng-click="ctrl.removeField($index)">
                <span class="glyphicon glyphicon-trash"></span>
                <span class="sr-only">Remove field</span>
              </a>
            </div>
          </td>
        </tr>
        </tbody>
        <tfoot>
        <tr>
          <td colspan="4">
            <button class="btn btn-block btn-sm add-new" ng-click="ctrl.addField()">
              <span class="glyphicon glyphicon-plus-sign"></span>
              Add Label
            </button>
          </td>
        </tr>
        </tfoot>
      </table>
    </div>
  `
};

export const KUBERNETES_MANIFEST_LABEL_EDITOR = 'spinnaker.kubernetes.v2.manifest.labelEditor.component';
module(KUBERNETES_MANIFEST_LABEL_EDITOR, []).component(
  'kubernetesManifestLabelEditor',
  kubernetesManifestLabelEditorComponent,
);
