/*
 * Copyright 2015 Google, Inc.
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

module.exports = angular.module('spinnaker.core.forms.checkmap.checkmap.directive', [
  require('../../utils/lodash.js'),
])
  .directive('checkmap', function(_) {
    return {
      restrict: 'E',
      templateUrl: require('./checkmap.directive.html'),
      scope: {
        // The map to display, key --> list of displayable items.
        itemMap: '=',
        // Array of selected items from the itemMap, regardless of key.
        selectedItems: '=',
        onChange: '&'
      },
      link: function(scope) {
        function initializeModelHolder() {
          scope.selectedItems = scope.selectedItems || [];
          // modelHolder is a map of key --> val --> "checked" boolean.
          scope.modelHolder = {};
          _.forEach(scope.itemMap, function(itemList, key) {
            scope.modelHolder[key] = {};
            _.forEach(itemList, function(item) {
              scope.modelHolder[key][item] = _.includes(scope.selectedItems, item);
            });
          });

          _.forEach(scope.selectedItems, function(selectedItem) {
            _.forEach(scope.modelHolder, function(itemList, key) {
              if (_.includes(itemList, selectedItem)) {
                scope.modelHolder[key][selectedItem] = true;
              }
            });
          });
        }

        function updateSelectedItems() {
          var newSelectedItems = [];
          _.forEach(scope.modelHolder, function(valMap) {
            _.forEach(valMap, function(selected, item) {
              if (selected) {
                newSelectedItems.push(item);
              }
            });
          });


          angular.copy(newSelectedItems, scope.selectedItems);

          if (scope.onChange) {
            scope.$evalAsync(scope.onChange);
          }
        }

        scope.updateSelectedItems = updateSelectedItems;

        scope.$watch('selectedItems', initializeModelHolder);
      }
    };
  });
