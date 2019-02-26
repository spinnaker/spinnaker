'use strict';

const angular = require('angular');

/***
 * Directive to allow optionally setting autofocus behavior of form elements
 *   <input auto-focus/> will not focus
 *   <input auto-focus="somethingThatIsTrue"/> will focus
 *   <input auto-focus="somethingThatIsFalse"/> will not focus
 *   <input autofocus/> will focus in the vast majority of browsers (not Angular - this is just HTML)
 *
 */
module.exports = angular.module('spinnaker.core.forms.autoFocus.directive', []).directive('autoFocus', [
  '$timeout',
  function($timeout) {
    return {
      restrict: 'A',
      link: function(scope, elem, attrs) {
        if (scope.$eval(attrs.autoFocus)) {
          $timeout(() => elem.focus());
        }
      },
    };
  },
]);
