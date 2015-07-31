'use strict';

require('../../views/directives/katoProgress.html');

module.exports = function () {
  return {
    restrict: 'E',
    templateUrl: require('../../views/directives/katoProgress.html'),
    scope: {
      taskStatus: '=',
      title: '@'
    }
  };
};
