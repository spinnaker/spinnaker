'use strict';

require('../../views/directives/panelprogress.html');

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
