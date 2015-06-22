'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    template: require('views/directives/gce/zoneSelectField.html'),
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
