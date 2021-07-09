import { ITimeoutService, module } from 'angular';
import React from 'react';

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
  controller: [
    '$timeout',
    function ($timeout: ITimeoutService) {
      this.handleSelectChanged = (event: React.ChangeEvent<HTMLInputElement>) => {
        // It seems event.persist() doesn't help here because the rerender updated target's value
        // so we need to capture it before that happens.
        const value = event.target.value;
        $timeout(() => {
          this.currentValue = this.component[this.field] = value;
          this.onChange && this.onChange();
        });
      };
    },
  ],
});
