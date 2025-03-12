'use strict';

import { module } from 'angular';

import { CloudProviderRegistry } from '../CloudProviderRegistry';
import { AccountService } from '../../account/AccountService';

export const CORE_CLOUDPROVIDER_PROVIDERSELECTION_PROVIDERSELECTOR_DIRECTIVE = 'spinnaker.providerSelection.directive';
export const name = CORE_CLOUDPROVIDER_PROVIDERSELECTION_PROVIDERSELECTOR_DIRECTIVE; // for backwards compatibility
module(CORE_CLOUDPROVIDER_PROVIDERSELECTION_PROVIDERSELECTOR_DIRECTIVE, [])
  .directive('providerSelector', [
    '$q',
    function ($q) {
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
        link: function (scope) {
          scope.initialized = false;
          const getProviderList = scope.providers ? $q.when(scope.providers.sort()) : AccountService.listProviders();
          getProviderList.then(function (providers) {
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
    function ($scope, $uibModalInstance, providerOptions) {
      $scope.command = {
        provider: '',
      };

      $scope.getImage = function (provider) {
        return CloudProviderRegistry.getValue(provider, 'logo.path');
      };

      $scope.providerOptions = providerOptions;

      this.selectProvider = function () {
        $uibModalInstance.close($scope.command.provider);
      };

      this.cancel = $uibModalInstance.dismiss;
    },
  ]);
