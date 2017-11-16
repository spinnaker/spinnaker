import { IComponentOptions, IController, module } from 'angular';

import { IKubernetesManifestCommand } from '../manifestCommandBuilder.service';

class KubernetesManifestBasicSettingsCtrl implements IController {
  public command: IKubernetesManifestCommand;
}

class KubernetesManifestBasicSettingsComponent implements IComponentOptions {
  public bindings: any = { command: '=' };
  public controller: any = KubernetesManifestBasicSettingsCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <div class="container-fluid form-horizontal">
      <ng-form name="basicSettings">
        <div class="form-group">
          <div class="col-md-3 sm-label-right">
            Account *
            <help-field key="kubernetes.manifest.account"></help-field>
          </div>
          <div class="col-md-7">
            <account-select-field component="ctrl.command"
                                  field="account"
                                  accounts="ctrl.command.backingData.accounts"
                                  provider="'kubernetes'"></account-select-field>
          </div>
        </div>

        <div class="form-group">
          <div class="col-md-3 sm-label-right">
            Application *
            <help-field key="kubernetes.manifest.application"></help-field>
          </div>
          <div class="col-md-7"><input readonly="true"
                                       type="text"
                                       class="form-control input-sm"
                                       name="application"
                                       ng-model="ctrl.command.moniker.app"/></div>
        </div>

        <div class="form-group">
          <div class="col-md-3 sm-label-right">
            Cluster *
            <help-field key="kubernetes.manifest.cluster"></help-field>
          </div>
          <div class="col-md-7"><input required
                                       type="text"
                                       class="form-control input-sm"
                                       name="cluster"
                                       ng-model="ctrl.command.moniker.cluster"/></div>
        </div>

        <div class="form-group">
          <div class="col-md-3 sm-label-right">
            <i>Stack</i>
            <help-field key="kubernetes.manifest.stackdetail"></help-field>
          </div>
          <div class="col-md-3"><input required
                                       type="text"
                                       class="form-control input-sm"
                                       name="stack"
                                       ng-model="ctrl.command.moniker.stack"/></div>
          <div class="col-md-1 sm-label-right">
            <i>Detail</i>
          </div>
          <div class="col-md-3"><input required
                                       type="text"
                                       class="form-control input-sm"
                                       name="detail"
                                       ng-model="ctrl.command.moniker.detail"/></div>
        </div>

        <div class="form-group">
        </div>
      </ng-form>
    </div>
  `;
}

export const KUBERNETES_MANIFEST_BASIC_SETTINGS = 'spinnaker.kubernetes.v2.kubernetes.manifest.basicSettings.component';
module(KUBERNETES_MANIFEST_BASIC_SETTINGS, [])
  .component('kubernetesManifestBasicSettings', new KubernetesManifestBasicSettingsComponent());
