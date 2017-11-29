import { IComponentOptions, IController, module } from 'angular';

import { IKubernetesManifestCommand, IKubernetesManifestCommandMetadata } from '../manifestCommandBuilder.service';

import './manifestEntry.less'

class KubernetesManifestCtrl implements IController {
  public command: IKubernetesManifestCommand;
  public metadata: IKubernetesManifestCommandMetadata;
  public change: () => void;
}

class KubernetesManifestEntryComponent implements IComponentOptions {
  public bindings: any = { command: '=', metadata: '=', change: '&' };
  public controller: any = KubernetesManifestCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <div class="container-fluid form-horizontal">
      <ng-form name="manifest">
        <div class="form-group">
          <textarea class="code form-control kubernetes-manifest-entry" ng-model="ctrl.metadata.manifestText" ng-change="ctrl.change()" rows="40"></textarea>
        </div>
      </ng-form>
    </div>
  `;
}

export const KUBERNETES_MANIFEST_ENTRY = 'spinnaker.kubernetes.v2.kubernetes.manifest.entry.component';
module(KUBERNETES_MANIFEST_ENTRY, [])
  .component('kubernetesManifestEntry', new KubernetesManifestEntryComponent());
