'use strict';

require('../../views/directives/katoError.html');

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
