import { IComponentOptions, IController, module } from 'angular';

import { AccountService, IAccountDetails, SETTINGS } from '@spinnaker/core';
import { KUBERNETES_MANIFEST_LABEL_EDITOR } from './labelEditor/labelEditor.component';
import { IMultiManifestSelector } from './IManifestSelector';

class KubernetesManifestSelectorCtrl implements IController {
  public selector: IMultiManifestSelector;
  public accounts: IAccountDetails[];
  public kindsMetadata: string;
  public selectorType: string;
  public rawKind: string;
  public rawName: string;

  public $onInit() {
    AccountService.getAllAccountDetailsForProvider('kubernetes', 'v2').then(accounts => {
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

    if (this.selector.manifestName) {
      [this.rawKind, this.rawName] = this.selector.manifestName.split(' ');
    }
  }

  public buildName(): void {
    this.selector.manifestName = this.rawKind + ' ' + this.rawName;
  }

  public stringToArray(): void {
    this.selector.kinds = this.kindsMetadata.split(',').map(e => e.trim());
  }

  public clearOldSelection(type: string): void {
    if (type === 'name') {
      delete this.selector.labelSelectors;
      this.selector.manifestName = '';
    } else {
      delete this.selector.manifestName;
      this.selector.labelSelectors = { selectors: [] };
    }
  }
}

const kubernetesMultiManifestSelectorComponent: IComponentOptions = {
  bindings: { selector: '=' },
  controller: KubernetesManifestSelectorCtrl,
  controllerAs: 'ctrl',
  template: `
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
      <stage-config-field label="Match On">
        <label class="radio-inline"><input type="radio" name="type" value="labels" ng-model="ctrl.selectorType" ng-click="ctrl.clearOldSelection('labels')">Labels</label>
        <label class="radio-inline"><input type="radio" name="type" value="name" ng-model="ctrl.selectorType" ng-click="ctrl.clearOldSelection('name')">Name</label>
      </stage-config-field>
      <div ng-if="ctrl.selectorType === 'labels'">
        <stage-config-field label="Kinds">
          <input type="text" placeholder="Comma seperated. Ex: deployment, replicaSet"
            class="form-control input-sm highlight-pristine"
            ng-model="ctrl.kindsMetadata" ng-change="ctrl.stringToArray()"/>
        </stage-config-field>
        <stage-config-field label="Labels">
          <kubernetes-manifest-label-editor selectors="ctrl.selector.labelSelectors.selectors"></kubernetes-manifest-label-editor>
        </stage-config-field>
      </div>
      <div ng-if="ctrl.selectorType === 'name'">
        <stage-config-field label="Kind">
          <input type="text" placeholder="e.g. deployment"
            class="form-control input-sm highlight-pristine"
            ng-model="ctrl.rawKind" ng-change="ctrl.buildName()"/>
        </stage-config-field>
        <stage-config-field label="Name">
          <input type="text"
            class="form-control input-sm highlight-pristine"
            ng-model="ctrl.rawName" ng-change="ctrl.buildName()"/>
        </stage-config-field>
      </div>
    </div>
  `,
};

export const KUBERNETES_MULTI_MANIFEST_SELECTOR = 'spinnaker.kubernetes.v2.multi.manifest.selector.component';
module(KUBERNETES_MULTI_MANIFEST_SELECTOR, [KUBERNETES_MANIFEST_LABEL_EDITOR]).component(
  'kubernetesMultiManifestSelector',
  kubernetesMultiManifestSelectorComponent,
);
