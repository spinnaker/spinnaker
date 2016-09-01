'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.viewSnapshotDiff.component', [
    require('../../cloudProvider/cloudProvider.registry.js'),
    require('../../account/account.service.js'),
    require('../../utils/lodash.js'),
    require('./snapshotDiff.modal.controller.js'),
  ])
  .component('viewSnapshotDiffButton', {
    bindings: {
      application: '='
    },
    template: `<button class="btn btn-link" ng-click="$ctrl.viewSnapshotDiffs()">
                  <span class="glyphicon glyphicon-cloud"></span> View Snapshot History
               </button>`,
    controller: function ($q, accountService, cloudProviderRegistry, $uibModal, _) {

      function getSnapshotEnabledAccounts (application) {
        return accountService.listProviders(application)
          .then((providers) => providers.filter(provider => cloudProviderRegistry.getValue(provider, 'snapshotsEnabled')))
          .then((snapshotEnabledProviders) => $q.all(snapshotEnabledProviders.map(provider => accountService.listAccounts(provider))))
          .then((accounts) => _(accounts)
            .flatten()
            .pluck('name')
            .valueOf());
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
