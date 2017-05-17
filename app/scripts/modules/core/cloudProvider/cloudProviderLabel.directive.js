'use strict';

const angular = require('angular');

import {CLOUD_PROVIDER_REGISTRY} from 'core/cloudProvider/cloudProvider.registry';

import './cloudProviderLogo.less';

module.exports = angular
  .module('spinnaker.core.cloudProviderLabel.directive', [
    CLOUD_PROVIDER_REGISTRY,
  ])
  .directive('cloudProviderLabel', function (cloudProviderRegistry) {
    return {
      restrict: 'E',
      template: '<span ng-bind="providerLabel"></span>',
      scope: {
        provider: '=',
      },
      link: function(scope) {
        function setProviderLabel() {
          scope.providerLabel = cloudProviderRegistry.getValue(scope.provider, 'name') || scope.provider;
        }
        scope.$watch('provider', setProviderLabel);
      },
    };
  });
