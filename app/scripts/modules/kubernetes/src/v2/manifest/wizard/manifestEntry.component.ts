import { IComponentOptions, IController, IScope, module } from 'angular';
import { dump } from 'js-yaml';

import { IManifest } from '@spinnaker/core';
import { IKubernetesManifestCommand } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

import './manifestEntry.less';

class KubernetesManifestCtrl implements IController {
  public command: IKubernetesManifestCommand;
  public manifests: IManifest[];
  public rawManifest: string;

  constructor(private $scope: IScope) {
    'ngInject';
  }

  // If we have more than one manifest, render as a
  // list of manifests. Otherwise, hide the fact
  // that the underlying model is a list.
  public $onInit = (): void => {
    const [first = null, ...rest] = this.manifests || [];
    const manifest = rest && rest.length ? this.manifests : first;
    try {
      this.rawManifest = manifest ? dump(manifest) : null;
    } catch (e) {
      this.rawManifest = null;
    }
  };

  public $onChanges = () => this.$onInit();

  public handleChange = (rawManifest: string, manifest: any): void => {
    this.rawManifest = rawManifest;
    if (!this.command.manifests) {
      this.command.manifests = [];
    }
    if (manifest) {
      Object.assign(this.command.manifests, Array.isArray(manifest) ? manifest : [manifest]);
    }
    this.$scope.$applyAsync();
  };
}

class KubernetesManifestEntryComponent implements IComponentOptions {
  public bindings = { command: '<', manifests: '<' };
  public controller = KubernetesManifestCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <yaml-editor
      value="ctrl.rawManifest"
      on-change="ctrl.handleChange"
    ></yaml-editor>`;
}

export const KUBERNETES_MANIFEST_ENTRY = 'spinnaker.kubernetes.v2.manifest.entry.component';
module(KUBERNETES_MANIFEST_ENTRY, []).component('kubernetesManifestEntry', new KubernetesManifestEntryComponent());
