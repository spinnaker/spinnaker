'use strict';

const angular = require('angular');

import { AccountService, Registry } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.openstack.pipeline.stage.shrinkClusterStage', [])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'shrinkCluster',
      cloudProvider: 'openstack',
      templateUrl: require('./shrinkClusterStage.html'),
      accountExtractor: stage => [stage.context.credentials],
      configAccountExtractor: stage => [stage.credentials],
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
        { type: 'requiredField', fieldName: 'regions' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('OpenstackShrinkClusterStageCtrl', [
    '$scope',
    function($scope) {
      var ctrl = this;

      let stage = $scope.stage;

      $scope.state = {
        accounts: false,
        regionsLoaded: false,
      };

      AccountService.listAccounts('openstack').then(function(accounts) {
        $scope.accounts = accounts;
        $scope.state.accounts = true;
      });

      stage.regions = stage.regions || [];
      stage.cloudProvider = 'openstack';

      if (!stage.credentials && $scope.application.defaultCredentials.openstack) {
        stage.credentials = $scope.application.defaultCredentials.openstack;
      }
      if (!stage.regions.length && $scope.application.defaultRegions.openstack) {
        stage.regions.push($scope.application.defaultRegions.openstack);
      }

      if (stage.shrinkToSize === undefined) {
        stage.shrinkToSize = 1;
      }

      if (stage.allowDeleteActive === undefined) {
        stage.allowDeleteActive = false;
      }

      ctrl.pluralize = function(str, val) {
        if (val === 1) {
          return str;
        }
        return str + 's';
      };

      if (stage.retainLargerOverNewer === undefined) {
        stage.retainLargerOverNewer = 'false';
      }
      stage.retainLargerOverNewer = stage.retainLargerOverNewer.toString();
    },
  ]);
