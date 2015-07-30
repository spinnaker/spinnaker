'use strict';

module.exports = function() {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: require('../../views/directives/panelprogress.html'),
    scope: {
      item: '=',
    },
  };
};
