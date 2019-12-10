'use strict';

const angular = require('angular');

export const GOOGLE_GCENETWORKSELECTFIELD_DIRECTIVE = 'spinnaker.google.networkSelectField.directive';
export const name = GOOGLE_GCENETWORKSELECTFIELD_DIRECTIVE; // for backwards compatibility
angular.module(GOOGLE_GCENETWORKSELECTFIELD_DIRECTIVE, []).directive('gceNetworkSelectField', function() {
  return {
    restrict: 'E',
    templateUrl: require('./networkSelectField.directive.html'),
    scope: {
      networks: '=',
      component: '=',
      field: '@',
      account: '=',
      onChange: '&',
      labelColumns: '@',
      fieldColumns: '@',
    },
  };
});
