import { ITimeoutService, module } from 'angular';
import * as React from 'react';

export const ACCOUNT_SELECT_COMPONENT = 'spinnaker.core.account.accountSelectField.component';

module(ACCOUNT_SELECT_COMPONENT, []).component('accountSelectField', {
  template: `
        <account-select-wrapper
          ng-if="$ctrl.accounts"
          accounts="$ctrl.accounts"
          provider="$ctrl.provider"
          read-only="$ctrl.readOnly"
          on-change="$ctrl.handleSelectChanged"
          value="$ctrl.component[$ctrl.field]"
          on-change="$ctrl.fieldChanged"></account-select-wrapper>
      `,
  bindings: {
    accounts: '=',
    component: '=',
    field: '@',
    provider: '=',
    onChange: '&',
    readOnly: '=',
  },
  controller: function($timeout: ITimeoutService) {
    this.handleSelectChanged = (event: React.ChangeEvent<HTMLInputElement>) => {
      $timeout(() => {
        this.currentValue = this.component[this.field] = event.target.value;
        this.onChange && this.onChange();
      });
    };
  },
});
