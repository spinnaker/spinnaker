import { IComponentOptions, IController, module } from 'angular';

import { AccountService, IAccountDetails, SETTINGS } from '@spinnaker/core';
import { KUBERNETES_MANIFEST_LABEL_EDITOR } from './labelEditor/labelEditor.component';
import { IManifestSelector } from './IManifestSelector';

class KubernetesManifestSelectorCtrl implements IController {
  public selector: IManifestSelector;
  public accounts: IAccountDetails[];
  public rawName: string;
  public rawKind: string;

  constructor(private accountService: AccountService) {
    'ngInject';
    this.accountService.getAllAccountDetailsForProvider('kubernetes', 'v2').then(accounts => {
      this.accounts = accounts;
      if (!this.selector.account) {
        if (this.accounts.length) {
          if (this.accounts.map(e => e.name).includes(SETTINGS.providers.kubernetes.defaults.account)) {
            this.selector.account = SETTINGS.providers.kubernetes.defaults.account;
          } else {
            this.selector.account = this.accounts[0].name;
          }
        }
      }
    });

    if (this.selector.manifestName) {
      [this.rawKind, this.rawName] = this.selector.manifestName.split(' ');
    }
  }

  public onNameChange(): void {
    this.selector.manifestName = this.rawKind + ' ' + this.rawName;
    this.selector.kind = this.rawKind;
  }
}

class KubernetesMultiManifestSelectorComponent implements IComponentOptions {
  public bindings: any = { selector: '=' };
  public controller: any = KubernetesManifestSelectorCtrl;
  public controllerAs = 'ctrl';
  public template = `
    <form name="manifestSelectorForm">
      <stage-config-field label="Account">
        <account-select-field component="ctrl.selector"
          field="account"
          accounts="ctrl.accounts"
          provider="'kubernetes'"></account-select-field>
      </stage-config-field>
      <stage-config-field label="Namespace">
        <input type="text"
          class="form-control input-sm highlight-pristine"
          ng-model="ctrl.selector.location"/>
      </stage-config-field>
      <stage-config-field label="Kind">
        <input type="text" placeholder="e.g. deployment"
          class="form-control input-sm highlight-pristine"
          ng-model="ctrl.rawKind" ng-change="ctrl.onNameChange()"/>
      </stage-config-field>
      <stage-config-field label="Name">
        <input type="text"
          class="form-control input-sm highlight-pristine"
          ng-model="ctrl.rawName" ng-change="ctrl.onNameChange()"/>
      </stage-config-field>
    </div>
  `;
}

export const KUBERNETES_MANIFEST_SELECTOR = 'spinnaker.kubernetes.v2.manifest.selector.component';
module(KUBERNETES_MANIFEST_SELECTOR, [
    KUBERNETES_MANIFEST_LABEL_EDITOR,
  ]).component('kubernetesManifestSelector', new KubernetesMultiManifestSelectorComponent());
