'use strict';

import * as angular from 'angular';
import _ from 'lodash';

export const CORE_FORMS_CHECKMAP_CHECKMAP_DIRECTIVE = 'spinnaker.core.forms.checkmap.checkmap.directive';
export const name = CORE_FORMS_CHECKMAP_CHECKMAP_DIRECTIVE; // for backwards compatibility
angular.module(CORE_FORMS_CHECKMAP_CHECKMAP_DIRECTIVE, []).directive('checkmap', function () {
  return {
    restrict: 'E',
    templateUrl: require('./checkmap.directive.html'),
    scope: {
      // The map to display, key --> list of displayable items.
      itemMap: '=',
      // Array of selected items from the itemMap, regardless of key.
      selectedItems: '=',
      onChange: '&',
    },
    link: function (scope) {
      function initializeModelHolder() {
        scope.selectedItems = scope.selectedItems || [];
        // modelHolder is a map of key --> val --> "checked" boolean.
        scope.modelHolder = {};
        _.forEach(scope.itemMap, function (itemList, key) {
          scope.modelHolder[key] = {};
          _.forEach(itemList, function (item) {
            scope.modelHolder[key][item] = _.includes(scope.selectedItems, item);
          });
        });

        _.forEach(scope.selectedItems, function (selectedItem) {
          _.forEach(scope.modelHolder, function (itemList, key) {
            if (_.includes(itemList, selectedItem)) {
              scope.modelHolder[key][selectedItem] = true;
            }
          });
        });
      }

      function updateSelectedItems() {
        const newSelectedItems = [];
        _.forEach(scope.modelHolder, function (valMap) {
          _.forEach(valMap, function (selected, item) {
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

      scope.$watch('itemMap', initializeModelHolder);
      scope.$watch('selectedItems', initializeModelHolder);
    },
  };
});
