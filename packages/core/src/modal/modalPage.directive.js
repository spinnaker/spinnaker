'use strict';

import { module } from 'angular';
const $ = require('jquery');

export const CORE_MODAL_MODALPAGE_DIRECTIVE = 'spinnaker.core.modal.modalPage.directive';
export const name = CORE_MODAL_MODALPAGE_DIRECTIVE; // for backwards compatibility
module(CORE_MODAL_MODALPAGE_DIRECTIVE, []).directive('modalPage', function () {
  return {
    restrict: 'EA',
    link: function (scope, elem, attrs) {
      if (attrs.modalPage === 'false') {
        return;
      }
      function getTabbableElements() {
        const tagSelector = 'a[href],input,select,button,textarea';
        return elem.find(tagSelector).filter(':visible').not(':disabled');
      }

      const ts = Math.floor(Math.random() * 4294967295);
      $(document).on('keydown.modalPage-' + ts, function (event) {
        if (event.keyCode === 9) {
          const $tabbableElements = getTabbableElements();
          const $firstElem = $tabbableElements[0];
          const $lastElem = $tabbableElements[$tabbableElements.length - 1];
          if ($firstElem === event.target && event.shiftKey) {
            $lastElem.focus();
            return false;
          }
          if ($lastElem === event.target && !event.shiftKey) {
            $firstElem.focus();
            return false;
          }
          if ($tabbableElements.index(event.target) === -1) {
            if (event.shiftKey) {
              $lastElem.focus();
            } else {
              if ($firstElem) {
                $firstElem.focus();
              }
            }
            return false;
          }
        }
      });

      scope.$on('$destroy', function () {
        $(document).off('.modalPage-' + ts);
      });
    },
  };
});
