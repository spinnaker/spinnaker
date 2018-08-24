import { IComponentOptions, IController, IScope, module } from 'angular';

import { IKubernetesManifestCommand } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

import './manifestEntry.less';

class KubernetesManifestCtrl implements IController {
  public command: IKubernetesManifestCommand;
  public initialValue: any;

  constructor(private $scope: IScope) {
    'ngInject';
  }

  // If we have more than one manifest, render as a
  // list of manifests. Otherwise, hide the fact
  // that the underlying model is a list.
  public $onInit = (): void => {
    const [first = null, ...rest] = this.command.manifests || [];
    this.initialValue = rest && rest.length ? this.command.manifests : first;
  };

  public handleChange = (manifests: any): void => {
    if (!this.command.manifests) {
      this.command.manifests = [];
    }
    Object.assign(this.command.manifests, Array.isArray(manifests) ? manifests : [manifests]);
    this.$scope.$applyAsync();
  };
}

class KubernetesManifestEntryComponent implements IComponentOptions {
  public bindings = { command: '<' };
  public controller = KubernetesManifestCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <yaml-editor
      value="ctrl.initialValue"
      on-change="ctrl.handleChange"
    ></yaml-editor>`;
}

export const KUBERNETES_MANIFEST_ENTRY = 'spinnaker.kubernetes.v2.manifest.entry.component';
module(KUBERNETES_MANIFEST_ENTRY, []).component('kubernetesManifestEntry', new KubernetesManifestEntryComponent());
