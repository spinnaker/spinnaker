'use strict';

const angular = require('angular');

import { AccountService } from 'core/account/AccountService';
import { CloudProviderRegistry } from 'core/cloudProvider';

module.exports = angular
  .module('spinnaker.providerSelection.directive', [])
  .directive('providerSelector', [
    '$q',
    function($q) {
      return {
        restrict: 'E',
        scope: {
          component: '=',
          field: '@',
          readOnly: '=',
          providers: '=?',
          onChange: '&',
        },
        templateUrl: require('./providerSelector.html'),
        link: function(scope) {
          scope.initialized = false;
          var getProviderList = scope.providers ? $q.when(scope.providers.sort()) : AccountService.listProviders();
          getProviderList.then(function(providers) {
            scope.initialized = true;
            if (!providers.length) {
              scope.component[scope.field] = 'aws';
            }
            if (providers.length === 1) {
              scope.component[scope.field] = providers[0];
            }
            if (providers.length > 1) {
              scope.providers = providers;
              scope.showSelector = true;
            }
          });
        },
      };
    },
  ])
  .controller('ProviderSelectCtrl', [
    '$scope',
    '$uibModalInstance',
    'providerOptions',
    function($scope, $uibModalInstance, providerOptions) {
      $scope.command = {
        provider: '',
      };

      $scope.getImage = function(provider) {
        return CloudProviderRegistry.getValue(provider, 'logo.path');
      };

      $scope.providerOptions = providerOptions;

      this.selectProvider = function() {
        $uibModalInstance.close($scope.command.provider);
      };

      this.cancel = $uibModalInstance.dismiss;
    },
  ]);
