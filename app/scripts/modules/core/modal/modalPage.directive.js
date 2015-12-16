'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.modal.modalPage.directive', [
  require('../utils/jQuery.js'),
])
  .directive('modalPage', function ($) {
    return {
      restrict: 'EA',
      link: function (scope, elem) {
        function getTabbableElements() {
          var tagSelector = 'a[href],input,select,button,textarea';
          return elem.find(tagSelector).filter(':visible').not(':disabled').not(elem.find('.ng-enter *'));
        }

        var ts = Math.floor(Math.random() * 4294967295);
        $(document).on('keydown.modalPage-' + ts, function (event) {
          if (event.keyCode === 9) {
            var $tabbableElements = getTabbableElements(),
              $firstElem = $tabbableElements[0],
              $lastElem = $tabbableElements[$tabbableElements.length - 1];
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
      }
    };
});
