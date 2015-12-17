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

let angular = require('angular');

module.exports = angular.module('spinnaker.core.forms.checklist.checklist.directive', [])
  .directive('checklist', function() {
    return {
      restrict: 'E',
      templateUrl: require('./checklist.directive.html'),
      scope: {
        items: '=',
        model: '=',
        onChange: '&',
        inline: '=',
        includeSelectAllButton: '=',
      },
      link: function(scope) {
        function initializeModelHolder() {
          scope.model = scope.model || [];
          scope.modelHolder = {};
          scope.model.forEach(function (val) {
            scope.modelHolder[val] = true;
          });
        }

        function updateModel() {
          var updatedModel = [];
          scope.items.forEach(function (testKey) {
            if (scope.modelHolder[testKey]) {
              updatedModel.push(testKey);
            }
          });

          angular.copy(updatedModel, scope.model);

          if (scope.onChange) {
            scope.$evalAsync(scope.onChange);
          }
        }

        function allItemsSelected() {
          var allSelected = true;
          scope.items.forEach(function (key) {
            if (!scope.modelHolder[key]) {
              allSelected = false;
            }
          });
          return allSelected;
        }

        scope.selectAllOrNone = function () {
          if (allItemsSelected()) {
            scope.items.forEach(function (key) {
              scope.modelHolder[key] = false;
            });
          } else {
            scope.items.forEach(function (key) {
              scope.modelHolder[key] = true;
            });
          }
          updateModel();
        };

        scope.selectButtonText = function () {
          if (allItemsSelected()) {
            return 'Deselect All';
          }
          return 'Select All';
        };

        scope.allItemsSelected = allItemsSelected;
        scope.updateModel = updateModel;

        scope.$watch('model', initializeModelHolder);

        scope.$watch('items', function(newOptions, oldOptions) {
          if (oldOptions && oldOptions !== newOptions) {
            oldOptions.forEach(function(oldOption) {
              if (newOptions.indexOf(oldOption) === -1) {
                delete scope.modelHolder[oldOption];
              }
            });
            updateModel();
          }
        });
      }
    };
  });
