'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    templateUrl: require('../../views/directives/katoError.html'),
    scope: {
      taskStatus: '=',
      title: '@'
    }
  };
};
