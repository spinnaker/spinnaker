'use strict';

module.exports = function () {
  return {
    restrict: 'E',
    template: require('views/directives/katoProgress.html'),
    scope: {
      taskStatus: '=',
      title: '@'
    }
  };
};
