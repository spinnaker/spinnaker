'use strict';

import * as angular from 'angular';

export const CORE_FORMS_CHECKLIST_CHECKLIST_DIRECTIVE = 'spinnaker.core.forms.checklist.checklist.directive';
export const name = CORE_FORMS_CHECKLIST_CHECKLIST_DIRECTIVE; // for backwards compatibility
angular.module(CORE_FORMS_CHECKLIST_CHECKLIST_DIRECTIVE, []).directive('checklist', () => {
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
    link: (scope) => {
      function initializeModelHolder() {
        scope.values = scope.values || {};
        scope.itemsNormalized = scope.itemsNormalized || [];
        scope.model = scope.model || [];
        scope.modelHolder = {};
        scope.model.forEach((val) => {
          scope.modelHolder[val] = true;
        });
      }

      function updateModel() {
        const updatedModel = [];
        scope.itemsNormalized.forEach((testKey) => {
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
        let allSelected = true;
        scope.itemsNormalized.forEach((key) => {
          if (!scope.modelHolder[key]) {
            allSelected = false;
          }
        });
        return allSelected;
      }

      function normalizeItems(items) {
        if (items) {
          if (_.isMap(items)) {
            items.forEach((value, key) => {
              scope.values[key] = value;
            });
            scope.itemsNormalized = Array.from(items, ([key]) => key);
          } else {
            items.forEach((item) => {
              scope.values[item] = item;
            });
            scope.itemsNormalized = items;
          }
        }
      }

      scope.selectAllOrNone = () => {
        if (allItemsSelected()) {
          scope.itemsNormalized.forEach((key) => {
            scope.modelHolder[key] = false;
          });
        } else {
          scope.itemsNormalized.forEach((key) => {
            scope.modelHolder[key] = true;
          });
        }
        updateModel();
      };

      scope.selectButtonText = () => {
        if (allItemsSelected()) {
          return 'Deselect All';
        }
        return 'Select All';
      };

      scope.allItemsSelected = allItemsSelected;
      scope.updateModel = updateModel;

      scope.$watch('model', initializeModelHolder);

      scope.$watch('items', (newOptions, oldOptions) => {
        normalizeItems(newOptions);
        if (oldOptions && oldOptions !== newOptions) {
          oldOptions.forEach((oldOption, oldKey) => {
            if (_.isMap(newOptions)) {
              if (!newOptions.has(oldKey)) {
                delete scope.modelHolder[oldKey];
              }
            } else {
              if (!newOptions.includes(oldOption)) {
                delete scope.modelHolder[oldOption];
              }
            }
          });
          updateModel();
        }
      });
    },
  };
});
