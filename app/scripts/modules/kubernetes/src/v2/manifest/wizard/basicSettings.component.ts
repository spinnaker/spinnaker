import { IComponentOptions, IController, module } from 'angular';

import {
  IKubernetesManifestCommand,
  IKubernetesManifestCommandMetadata,
} from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

class KubernetesManifestBasicSettingsCtrl implements IController {
  public command: IKubernetesManifestCommand;
  public metadata: IKubernetesManifestCommandMetadata;
}

const kubernetesManifestBasicSettingsComponent: IComponentOptions = {
  bindings: { command: '=', metadata: '<' },
  controller: KubernetesManifestBasicSettingsCtrl,
  controllerAs: 'ctrl',
  template: `
      <ng-form name="basicSettings">
        <stage-config-field label="Account" help-key="kubernetes.manifest.account">
          <account-select-field component="ctrl.command"
                                field="account"
                                accounts="ctrl.metadata.backingData.accounts"
                                provider="'kubernetes'"></account-select-field>
        </stage-config-field>
        <stage-config-field label="Application" help-key="kubernetes.manifest.application">
          <input readonly="true"
                 type="text"
                 class="form-control input-sm"
                 name="application"
                 ng-model="ctrl.command.moniker.app"/>
        </stage-config-field>
      </ng-form>
  `
};

export const KUBERNETES_MANIFEST_BASIC_SETTINGS = 'spinnaker.kubernetes.v2.manifest.basicSettings.component';
module(KUBERNETES_MANIFEST_BASIC_SETTINGS, []).component(
  'kubernetesManifestBasicSettings',
  kubernetesManifestBasicSettingsComponent,
);
