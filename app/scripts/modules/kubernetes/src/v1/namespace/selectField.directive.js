'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_NAMESPACE_SELECTFIELD_DIRECTIVE = 'spinnaker.kubernetes.namespace.selectField.directive';
export const name = KUBERNETES_V1_NAMESPACE_SELECTFIELD_DIRECTIVE; // for backwards compatibility
module(KUBERNETES_V1_NAMESPACE_SELECTFIELD_DIRECTIVE, []).directive('namespaceSelectField', function() {
  return {
    restrict: 'E',
    templateUrl: require('./selectField.directive.html'),
    scope: {
      namespaces: '=',
      component: '=',
      field: '@',
      columns: '@',
      account: '=',
      onChange: '&',
      hideLabel: '=',
    },
  };
});
