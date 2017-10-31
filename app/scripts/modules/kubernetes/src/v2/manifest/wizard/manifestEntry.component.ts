import { IComponentOptions, IController, module } from 'angular';

import { IKubernetesManifestCommand } from '../manifestCommandBuilder.service';

import './manifestEntry.less'

class KubernetesManifestCtrl implements IController {
  public command: IKubernetesManifestCommand;
}

class KubernetesManifestEntryComponent implements IComponentOptions {
  public bindings: any = { command: '=' };
  public controller: any = KubernetesManifestCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <div class="container-fluid form-horizontal">
      <ng-form name="manifest">
        <div class="form-group">
          <textarea class="code form-control kubernetes-manifest-entry" ng-model="ctrl.command.manifestText" rows="40"></textarea>
        </div>
      </ng-form>
    </div>
  `;
}

export const KUBERNETES_MANIFEST_ENTRY = 'spinnaker.kubernetes.v2.kubernetes.manifest.entry.component';
module(KUBERNETES_MANIFEST_ENTRY, [])
  .component('kubernetesManifestEntry', new KubernetesManifestEntryComponent());
