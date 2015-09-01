'use strict';

module.exports = function() {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: require('./panelProgress.directive.html'),
    scope: {
      item: '=',
    },
  };
};
