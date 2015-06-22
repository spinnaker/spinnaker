'use strict';

module.exports = function() {
  return {
    restrict: 'E',
    replace: true,
    template: require('views/directives/panelprogress.html'),
    scope: {
      item: '=',
    },
  };
};
