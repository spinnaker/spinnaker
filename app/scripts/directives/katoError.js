'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    template: require('views/directives/katoError.html'),
    scope: {
      taskStatus: '=',
      title: '@'
    }
  };
};
