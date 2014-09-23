'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .directive('modalPage', function ($) {
    return {
      restrict: 'EA',
      link: function (scope, elem) {
        var $elem = $(elem);
        function getTabbableElements() {
          return $elem.find('a[href],input,select,button,textarea').filter(':visible').not(':disabled');
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
            if (!$.contains($elem.get(0), event.target)) {
              if (event.shiftKey) {
                $lastElem.focus();
              } else {
                $firstElem.focus();
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
  }
);
