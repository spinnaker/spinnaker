'use strict';

import { module } from 'angular';

/***
 * Directive to allow optionally setting autofocus behavior of form elements
 *   <input auto-focus/> will not focus
 *   <input auto-focus="somethingThatIsTrue"/> will focus
 *   <input auto-focus="somethingThatIsFalse"/> will not focus
 *   <input autofocus/> will focus in the vast majority of browsers (not Angular - this is just HTML)
 *
 */
export const CORE_FORMS_AUTOFOCUS_AUTOFOCUS_DIRECTIVE = 'spinnaker.core.forms.autoFocus.directive';
export const name = CORE_FORMS_AUTOFOCUS_AUTOFOCUS_DIRECTIVE; // for backwards compatibility
module(CORE_FORMS_AUTOFOCUS_AUTOFOCUS_DIRECTIVE, []).directive('autoFocus', [
  '$timeout',
  function ($timeout) {
    return {
      restrict: 'A',
      link: function (scope, elem, attrs) {
        if (scope.$eval(attrs.autoFocus)) {
          $timeout(() => elem.focus());
        }
      },
    };
  },
]);
