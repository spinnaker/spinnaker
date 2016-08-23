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

      function getSnapshotEnabledCloudProviders (application) {
        let cloudProviders = _.get(application, 'attributes.cloudProviders');
        if (_.isString(cloudProviders)) {
          return cloudProviders
            .split(',')
            .filter(provider => cloudProviderRegistry.getValue(provider, 'snapshotsEnabled'));
        }
        return [];
      }

      this.viewSnapshotDiffs = () => {
        let cloudProviders = getSnapshotEnabledCloudProviders(this.application);
        let accountsForApplication = this.application.attributes.accounts.split(',');
        let availableAccountsPromise = $q.all(cloudProviders.map(provider => accountService.listAccounts(provider)))
          .then((results) => {
            return _(results)
              .flatten()
              .pluck('name')
              .intersection(accountsForApplication)
              .valueOf();
          });

        $uibModal.open({
          templateUrl: require('./snapshotDiff.modal.html'),
          controller: 'SnapshotDiffModalCtrl',
          controllerAs: 'ctrl',
          size: 'lg modal-fullscreen',
          resolve: {
            availableAccounts: () => availableAccountsPromise,
            application: () => this.application
          }
        });
      };
    }
  });
