'use strict';

const angular = require('angular');

import { CloudProviderRegistry } from 'core/cloudProvider';

import './cloudProviderLogo.less';

module.exports = angular
  .module('spinnaker.core.cloudProviderLabel.directive', [])
  .directive('cloudProviderLabel', function() {
    return {
      restrict: 'E',
      template: '<span ng-bind="providerLabel"></span>',
      scope: {
        provider: '=',
      },
      link: function(scope) {
        function setProviderLabel() {
          scope.providerLabel = CloudProviderRegistry.getValue(scope.provider, 'name') || scope.provider;
        }
        scope.$watch('provider', setProviderLabel);
      },
    };
  });
