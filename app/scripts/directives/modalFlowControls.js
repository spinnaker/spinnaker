/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

angular.module('deckApp')
  .directive('multiPageModal', function() {
    return {
      restrict: 'A',
      controller: function($scope) {
        $scope.page = 0;
        $scope.nextPage = function() {
          $scope.page += 1;
          $scope.stage.removeClass('back').addClass('forward');
        };
        $scope.previousPage = function() {
          $scope.page -= 1;
          $scope.stage.removeClass('forward').addClass('back');
        };
      },
      link: function (scope, elem) {
        scope.stage = elem;
      }
    };
  })
  .directive('modalPage', function($) {
    return {
      restrict: 'EA',
      link: function(scope, elem) {
        function getTabbableElements() {
          return elem.find('a,input,select,button,textarea').filter(':visible').not(':disabled');
        }
        var ts = Math.floor(Math.random() * 4294967295);
        $(document).on('keydown.modalPage-'+ts, function(event) {
          if (event.keyCode === 9) {
            var $tabbableElements = getTabbableElements(),
              $firstElem = $tabbableElements[0],
              $lastElem = $tabbableElements[$tabbableElements.length-1];
            if ($firstElem === event.target && event.shiftKey) {
              $lastElem.focus();
              return false;
            }
            if ($lastElem === event.target && !event.shiftKey) {
              $firstElem.focus();
              return false;
            }
            if (!$.contains(elem.get(0), event.target)) {
              if (event.shiftKey) {
                $lastElem.focus();
              } else {
                $firstElem.focus();
              }
              return false;
            }
          }
        });

        elem.on('$destroy', function() {
          $(document).off('.modalPage-'+ts);
        });
      }
    };
  });
