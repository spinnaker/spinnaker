'use strict';

import { module } from 'angular';

import { CloudProviderRegistry } from './CloudProviderRegistry';

import './cloudProviderLogo.less';

export const CORE_CLOUDPROVIDER_CLOUDPROVIDERLABEL_DIRECTIVE = 'spinnaker.core.cloudProviderLabel.directive';
export const name = CORE_CLOUDPROVIDER_CLOUDPROVIDERLABEL_DIRECTIVE; // for backwards compatibility
module(CORE_CLOUDPROVIDER_CLOUDPROVIDERLABEL_DIRECTIVE, []).directive('cloudProviderLabel', function () {
  return {
    restrict: 'E',
    template: '<span ng-bind="providerLabel"></span>',
    scope: {
      provider: '=',
    },
    link: function (scope) {
      function setProviderLabel() {
        scope.providerLabel = CloudProviderRegistry.getValue(scope.provider, 'name') || scope.provider;
      }
      scope.$watch('provider', setProviderLabel);
    },
  };
});
