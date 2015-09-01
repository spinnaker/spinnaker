'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    templateUrl: require('./zoneSelectField.directive.html'),
    scope: {
      zones: '=',
      component: '=',
      field: '@',
      account: '=',
      onChange: '&',
      labelColumns: '@'
    }
  };
};
