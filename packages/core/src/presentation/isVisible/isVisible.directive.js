'use strict';

import { module } from 'angular';

export const CORE_PRESENTATION_ISVISIBLE_ISVISIBLE_DIRECTIVE = 'spinnaker.core.presentation.isVisible.directive';
export const name = CORE_PRESENTATION_ISVISIBLE_ISVISIBLE_DIRECTIVE; // for backwards compatibility
module(CORE_PRESENTATION_ISVISIBLE_ISVISIBLE_DIRECTIVE, []).directive('isVisible', function () {
  return function (scope, element, attr) {
    scope.$watch(attr.isVisible, function (visible) {
      element.css('visibility', visible ? 'visible' : 'hidden');
    });
  };
});
