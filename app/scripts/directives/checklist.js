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

require('../app');

angular.module('deckApp')
  .directive('checklist', function() {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/checklist.html',
      scope: {
        items: '=',
        model: '=',
        onChange: '&'
      },
      link: function(scope) {
        var initialValues = scope.model || [];
        scope.modelHolder = {};
        initialValues.forEach(function(val) {
          scope.modelHolder[val] = val;
        });
        scope.updateModel = function() {
          var updatedModel = [];
          scope.items.forEach(function(testKey) {
            if (scope.modelHolder[testKey]) {
              updatedModel.push(testKey);
            }
          });
          scope.model = updatedModel;
          if (scope.onChange) {
            scope.$evalAsync(scope.onChange);
          }
        };

        scope.$watch('items', function(newOptions, oldOptions) {
          if (oldOptions) {
            oldOptions.forEach(function(oldOption) {
              if (newOptions.indexOf(oldOption) === -1) {
                delete scope.modelHolder[oldOption];
              }
            });
          }
        });
      }
    };
  });
