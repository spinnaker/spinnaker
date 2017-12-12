import { IComponentOptions, IController, module } from 'angular';

import { AccountService, IAccountDetails, SETTINGS } from '@spinnaker/core';
import { KUBERNETES_MANIFEST_LABEL_EDITOR } from './labelEditor/labelEditor.component';
import { IManifestSelector } from './IManifestSelector';

class KubernetesManifestSelectorCtrl implements IController {
  public selector: IManifestSelector;
  public accounts: IAccountDetails[];
  public kindsMetadata: string;
  public selectorType: string;

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
    this.kindsMetadata = this.selector.kinds.join(', ');
    if (this.selector.manifestName) {
      this.selectorType = 'name';
    } else {
      this.selectorType = 'labels';
    }
  }

  public stringToArray(): void {
    this.selector.kinds = this.kindsMetadata.split(',').map( e => e.trim() );
  }

  public clearOldSelection(type: string): void {
    if (type === 'name') {
      delete(this.selector.labelSelectors);
      this.selector.manifestName = '';
    } else {
      delete(this.selector.manifestName);
      this.selector.labelSelectors = { selectors: [] };
    }
  }
}

class KubernetesManifestSelectorComponent implements IComponentOptions {
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
      <stage-config-field label="Kinds">
        <input type="text" placeholder="Comma seperated. Ex: deployment, replicaSet"
          class="form-control input-sm highlight-pristine"
          ng-model="ctrl.kindsMetadata" ng-change="ctrl.stringToArray()"/>
      </stage-config-field>
      <stage-config-field label="Match On">
        <label class="radio-inline"><input type="radio" name="type" value="labels" ng-model="ctrl.selectorType" ng-click="ctrl.clearOldSelection('labels')">Labels</label>
        <label class="radio-inline"><input type="radio" name="type" value="name" ng-model="ctrl.selectorType" ng-click="ctrl.clearOldSelection('name')">Name</label>
      </stage-config-field>
      <stage-config-field label="Name" ng-if="ctrl.selectorType === 'name'">
        <input type="text" placeholder="Optional"
          class="form-control input-sm highlight-pristine"
          ng-model="ctrl.selector.manifestName"/>
      </stage-config-field>
      <stage-config-field label="Labels" ng-if="ctrl.selectorType === 'labels'">
        <kubernetes-manifest-label-editor selectors="ctrl.selector.labelSelectors.selectors"></kubernetes-manifest-label-editor>
      </stage-config-field>
    </div>
  `;
}

export const KUBERNETES_MANIFEST_SELECTOR = 'spinnaker.kubernetes.v2.manifest.selector.component';
module(KUBERNETES_MANIFEST_SELECTOR, [
    KUBERNETES_MANIFEST_LABEL_EDITOR,
  ]).component('kubernetesManifestSelector', new KubernetesManifestSelectorComponent());
