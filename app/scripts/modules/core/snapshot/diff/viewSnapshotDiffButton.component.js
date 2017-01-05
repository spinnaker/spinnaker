'use strict';

import _ from 'lodash';

import {ACCOUNT_SERVICE} from 'core/account/account.service';
import {CLOUD_PROVIDER_REGISTRY} from 'core/cloudProvider/cloudProvider.registry';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.viewSnapshotDiff.component', [
    CLOUD_PROVIDER_REGISTRY,
    ACCOUNT_SERVICE,
    require('./snapshotDiff.modal.controller.js'),
  ])
  .component('viewSnapshotDiffButton', {
    bindings: {
      application: '='
    },
    template: `<button class="btn btn-link" ng-click="$ctrl.viewSnapshotDiffs()">
                  <span class="glyphicon glyphicon-cloud"></span> View Snapshot History
               </button>`,
    controller: function ($q, accountService, cloudProviderRegistry, $uibModal) {

      function getSnapshotEnabledAccounts (application) {
        return accountService.listProviders(application)
          .then((providers) => providers.filter(provider => cloudProviderRegistry.getValue(provider, 'snapshotsEnabled')))
          .then((snapshotEnabledProviders) => $q.all(snapshotEnabledProviders.map(provider => accountService.listAccounts(provider))))
          .then((accounts) => _.chain(accounts)
            .flatten()
            .map('name')
            .value());
      }

      this.viewSnapshotDiffs = () => {
        $uibModal.open({
          templateUrl: require('./snapshotDiff.modal.html'),
          controller: 'SnapshotDiffModalCtrl',
          controllerAs: 'ctrl',
          size: 'lg modal-fullscreen',
          resolve: {
            availableAccounts: () => getSnapshotEnabledAccounts(this.application),
            application: () => this.application
          }
        });
      };
    }
  });
