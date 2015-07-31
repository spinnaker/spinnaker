'use strict';

require('../../views/directives/modalClose.html');

module.exports = function () {
  return {
    scope: true,
    restrict: 'E',
    templateUrl: require('../../views/directives/modalClose.html'),
  };
};
