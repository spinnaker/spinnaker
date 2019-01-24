'use strict';

import _ from 'lodash';

import { AccountService } from 'core/account/AccountService';
import { CloudProviderRegistry } from 'core/cloudProvider';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.deck.core.viewSnapshotDiff.component', [require('./snapshotDiff.modal.controller').name])
  .component('viewSnapshotDiffButton', {
    bindings: {
      application: '=',
    },
    template: `<button class="btn btn-link" ng-click="$ctrl.viewSnapshotDiffs()">
                  <span class="glyphicon glyphicon-cloud"></span> View Snapshot History
               </button>`,
    controller: function($q, $uibModal) {
      function getSnapshotEnabledAccounts(application) {
        return AccountService.listProviders(application)
          .then(providers => providers.filter(provider => CloudProviderRegistry.getValue(provider, 'snapshotsEnabled')))
          .then(snapshotEnabledProviders =>
            $q.all(snapshotEnabledProviders.map(provider => AccountService.listAccounts(provider))),
          )
          .then(accounts =>
            _.chain(accounts)
              .flatten()
              .map('name')
              .value(),
          );
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
  });
