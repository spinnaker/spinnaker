'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { AccountService } from '../../account/AccountService';
import { CloudProviderRegistry } from '../../cloudProvider';

import { CORE_SNAPSHOT_DIFF_SNAPSHOTDIFF_MODAL_CONTROLLER } from './snapshotDiff.modal.controller';

export const CORE_SNAPSHOT_DIFF_VIEWSNAPSHOTDIFFBUTTON_COMPONENT = 'spinnaker.deck.core.viewSnapshotDiff.component';
export const name = CORE_SNAPSHOT_DIFF_VIEWSNAPSHOTDIFFBUTTON_COMPONENT; // for backwards compatibility
module(CORE_SNAPSHOT_DIFF_VIEWSNAPSHOTDIFFBUTTON_COMPONENT, [
  CORE_SNAPSHOT_DIFF_SNAPSHOTDIFF_MODAL_CONTROLLER,
]).component('viewSnapshotDiffButton', {
  bindings: {
    application: '=',
  },
  template: `<button class="btn btn-link" ng-click="$ctrl.viewSnapshotDiffs()">
                  <span class="glyphicon glyphicon-cloud"></span> View Snapshot History
               </button>`,
  controller: [
    '$q',
    '$uibModal',
    function ($q, $uibModal) {
      function getSnapshotEnabledAccounts(application) {
        return AccountService.listProviders(application)
          .then((providers) =>
            providers.filter((provider) => CloudProviderRegistry.getValue(provider, 'snapshotsEnabled')),
          )
          .then((snapshotEnabledProviders) =>
            $q.all(snapshotEnabledProviders.map((provider) => AccountService.listAccounts(provider))),
          )
          .then((accounts) => _.chain(accounts).flatten().map('name').value());
      }

      this.viewSnapshotDiffs = () => {
        $uibModal.open({
          templateUrl: require('./snapshotDiff.modal.html'),
          controller: 'SnapshotDiffModalCtrl',
          controllerAs: 'ctrl',
          size: 'lg modal-fullscreen',
          resolve: {
            availableAccounts: () => getSnapshotEnabledAccounts(this.application),
            application: () => this.application,
          },
        });
      };
    },
  ],
});
