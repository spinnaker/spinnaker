'use strict';

import { module } from 'angular';

export const GOOGLE_GCEREGIONSELECTFIELD_DIRECTIVE = 'spinnaker.google.regionSelectField.directive';
export const name = GOOGLE_GCEREGIONSELECTFIELD_DIRECTIVE; // for backwards compatibility
module(GOOGLE_GCEREGIONSELECTFIELD_DIRECTIVE, []).directive('gceRegionSelectField', function () {
  return {
    restrict: 'E',
    templateUrl: require('./regionSelectField.directive.html'),
    scope: {
      regions: '=',
      component: '=',
      field: '@',
      account: '=',
      onChange: '&',
      labelColumns: '@',
      readOnly: '=',
      fieldColumns: '@',
    },
  };
});
