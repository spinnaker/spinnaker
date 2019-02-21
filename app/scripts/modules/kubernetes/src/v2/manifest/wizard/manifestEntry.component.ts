import { IComponentOptions, IController, IScope, module } from 'angular';

import { IManifest } from '@spinnaker/core';

import { IKubernetesManifestCommand } from 'kubernetes/v2/manifest/manifestCommandBuilder.service';
import { yamlDocumentsToString } from 'kubernetes/v2/manifest/editor/yaml/yamlEditorUtils';

import './manifestEntry.less';

class KubernetesManifestCtrl implements IController {
  public command: IKubernetesManifestCommand;
  public manifests: IManifest[];
  public rawManifest: string;

  public static $inject = ['$scope'];
  constructor(private $scope: IScope) {}

  // If we have more than one manifest, render as a
  // list of manifests. Otherwise, hide the fact
  // that the underlying model is a list.
  public $onInit = (): void => {
    this.rawManifest = yamlDocumentsToString(this.manifests);
  };

  public handleChange = (rawManifest: string, manifests: any): void => {
    this.rawManifest = rawManifest;
    this.command.manifests = manifests;
    this.$scope.$applyAsync();
  };
}

const kubernetesManifestEntryComponent: IComponentOptions = {
  bindings: { command: '<', manifests: '<' },
  controller: KubernetesManifestCtrl,
  controllerAs: 'ctrl',
  template: `
    <yaml-editor
      value="ctrl.rawManifest"
      on-change="ctrl.handleChange"
    ></yaml-editor>`,
};

export const KUBERNETES_MANIFEST_ENTRY = 'spinnaker.kubernetes.v2.manifest.entry.component';
module(KUBERNETES_MANIFEST_ENTRY, []).component('kubernetesManifestEntry', kubernetesManifestEntryComponent);
