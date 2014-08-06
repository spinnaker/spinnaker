'use strict';

module.exports = function () {
  return {
    templateUrl: 'views/application/healthCounts.html',
    restrict: 'E',
    replace: true,
    scope: {
      container: '='
    }
  };
};
