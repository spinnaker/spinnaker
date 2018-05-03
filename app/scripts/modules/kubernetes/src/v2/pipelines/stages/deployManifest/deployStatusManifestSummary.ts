import { IComponentOptions, IController, IScope, module } from 'angular';
import { KUBERNETES_DEPLOY_MANIFEST_MANIFEST_STATUS } from './manifestStatus.component';
import { KubernetesManifestService } from '../../../manifest/manifest.service';

import { KUBERNETES_SHOW_MANIFEST_YAML } from '../../../manifest/showManifestYaml.component';
import { KUBERNETES_SHOW_MANIFEST_DETAILS } from '../../../manifest/showManifestDetails.component';

class KubernetesDeployManifestDeployStatusManifestSummaryController implements IController {
  public manifestContents: any;
  public stage: any;
  public application: any;
  public manifest: any;

  constructor(private kubernetesManifestService: KubernetesManifestService, private $scope: IScope) {
    'ngInject';
    const params = {
      account: this.stage.context.account,
      location: this.manifestContents.metadata.namespace,
      name: this.manifestFullName(),
    };
    this.kubernetesManifestService.makeManifestRefresher(this.application, this.$scope, params, this);
  }

  private manifestFullName(): string {
    return this.normalizeKind(this.manifestContents.kind) + ' ' + this.manifestContents.metadata.name;
  }

  private normalizeKind(kind: string): string {
    return kind.charAt(0).toUpperCase() + kind.slice(1);
  }
}

class KubernetesDeployManifestDeployStatusManifestSummary implements IComponentOptions {
  public bindings: any = { manifestContents: '<', stage: '<', application: '<' };
  public controller: any = KubernetesDeployManifestDeployStatusManifestSummaryController;
  public controllerAs = 'ctrl';
  public template = `
    <kubernetes-deploy-manifest-manifest-status ng-if="ctrl.manifest" manifest="ctrl.manifest"></kubernetes-deploy-manifest-manifest-status>
    <br>
    <kubernetes-show-manifest-yaml manifest="ctrl.manifestContents" link-name="'YAML'"></kubernetes-show-manifest-yaml>
    <kubernetes-show-manifest-details
      ng-if="ctrl.manifest"
      application="ctrl.application"
      manifest="ctrl.manifest"
      account-id="ctrl.stage.context.account"
      link-name="'Details'"
    ></kubernetes-show-manifest-details>
  `;
}

export const KUBERNETES_DEPLOY_MANIFEST_DEPLOY_STATUS_MANIFEST_SUMMARY =
  'spinnaker.kubernetes.v2.kubernetes.deployManifest.deployStatusManifestSummary.component';
module(KUBERNETES_DEPLOY_MANIFEST_DEPLOY_STATUS_MANIFEST_SUMMARY, [
  KUBERNETES_DEPLOY_MANIFEST_MANIFEST_STATUS,
  KUBERNETES_SHOW_MANIFEST_YAML,
  KUBERNETES_SHOW_MANIFEST_DETAILS,
]).component(
  'kubernetesDeployManifestDeployStatusManifestSummary',
  new KubernetesDeployManifestDeployStatusManifestSummary(),
);
