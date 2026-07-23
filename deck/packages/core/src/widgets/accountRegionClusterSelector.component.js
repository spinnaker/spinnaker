'use strict';

import { module } from 'angular';
import React from 'react';
import ReactDOM from 'react-dom';

import { AccountRegionClusterSelector } from './AccountRegionClusterSelector';

export const CORE_WIDGETS_ACCOUNTREGIONCLUSTERSELECTOR_COMPONENT =
  'spinnaker.core.accountRegionClusterSelector.directive';
export const name = CORE_WIDGETS_ACCOUNTREGIONCLUSTERSELECTOR_COMPONENT; // for backwards compatibility

export const accountRegionClusterSelectorComponent = {
  bindings: {
    application: '<',
    component: '<',
    accounts: '<',
    clusterField: '@',
    singleRegion: '<',
    showAllRegions: '<?',
    onAccountUpdate: '&?',
    disableRegionSelect: '<?',
    showClusterSelect: '<?',
  },
  controller: class AccountRegionClusterSelectorController {
    static $inject = ['$element'];

    constructor($element) {
      this.$element = $element;
      this.linked = false;
    }

    $postLink() {
      this.linked = true;
      this.render();
    }

    $onChanges() {
      if (this.linked) {
        this.render();
      }
    }

    $onDestroy() {
      ReactDOM.unmountComponentAtNode(this.$element[0]);
    }

    render() {
      ReactDOM.render(
        React.createElement(AccountRegionClusterSelector, {
          accounts: this.accounts,
          application: this.application,
          clusterField: this.clusterField,
          component: this.component,
          disableRegionSelect: this.disableRegionSelect,
          onAccountUpdate: this.onAccountUpdate ? (account) => this.notifyAccountUpdate(account) : undefined,
          showAllRegions: this.showAllRegions,
          showClusterSelect: this.showClusterSelect,
          singleRegion: this.singleRegion,
        }),
        this.$element[0],
      );
    }

    notifyAccountUpdate(account) {
      const result = this.onAccountUpdate({ account });
      if (typeof result === 'function') {
        result(account);
      }
    }
  },
};

module(CORE_WIDGETS_ACCOUNTREGIONCLUSTERSELECTOR_COMPONENT, []).component(
  'accountRegionClusterSelector',
  accountRegionClusterSelectorComponent,
);
