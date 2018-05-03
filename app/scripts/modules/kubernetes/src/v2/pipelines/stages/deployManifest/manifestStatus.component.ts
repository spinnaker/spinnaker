import { IComponentOptions, IController, module } from 'angular';

const STATUS_PILLS = {
  available: 'success',
  stable: 'success',
  paused: 'warn',
  failed: 'danger',
};

class KubernetesDeployManifestManifestStatusCtrl implements IController {
  public manifest: any;
  public statusPills = STATUS_PILLS;
}

class KubernetesDeployManifestManifestStatusComponent implements IComponentOptions {
  public bindings: any = { manifest: '<' };
  public controller: any = KubernetesDeployManifestManifestStatusCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <span ng-repeat="(status, statusDescription) in ctrl.manifest.status">
      <span ng-if="statusDescription.state" class="pill {{ ctrl.statusPills[status] }}" title="{{ statusDescription.message }}">
        {{ status }}
      </span>
    </span>
  `;
}

export const KUBERNETES_DEPLOY_MANIFEST_MANIFEST_STATUS =
  'spinnaker.kubernetes.v2.kubernetes.deployManifest.manifest.status.component';
module(KUBERNETES_DEPLOY_MANIFEST_MANIFEST_STATUS, []).component(
  'kubernetesDeployManifestManifestStatus',
  new KubernetesDeployManifestManifestStatusComponent(),
);
