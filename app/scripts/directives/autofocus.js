'use strict';

module.exports = function($timeout) {
  return {
    restrict: 'A',
    link: function(scope, elem) {
      $timeout(function() { elem.focus(); });
    }
  };
};
