'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    template: require('views/directives/gce/regionSelectField.html'),
    scope: {
      regions: '=',
      component: '=',
      field: '@',
      account: '=',
      onChange: '&',
      labelColumns: '@',
      readOnly: '=',
    }
  };
};
