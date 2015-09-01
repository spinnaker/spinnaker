'use strict';

module.exports = function () {
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
    }
  };
};
