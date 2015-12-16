'use strict';

let angular = require('angular');

require('./cloudProviderLogo.less');

module.exports = angular
  .module('spinnaker.core.cloudProviderLabel.directive', [
    require('./cloudProvider.registry.js'),
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
