'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.selectField.directive', [
  require('../../core/utils/lodash'),
])
  .directive('selectField', function (_) {
    return {
      restrict: 'E',
      templateUrl: require('./selectField.directive.html'),
      scope: {
        label: '@',
        labelColumnSize: '@',
        helpKey: '@',
        model: '=',
        options: '=',
        onChange: '&',
        readOnly: '=',
        allowNoSelection: '=',
        noOptionsMessage: '@',
        noSelectionMessage: '@'
      },
      link: function(scope) {
        _.defaults(scope, {
          noOptionsMessage: '(No options available)',
          noSelectionMessage: '(No selection)',
          labelColumnSize: 3
        });

        scope.allowNoSelection = scope.allowNoSelection && !scope.readOnly;

        function updateSelectedOption() {
          var previousSelection = scope.model;

          scope.selectedOption = undefined;
          if (scope.options && scope.options.length) {
            if (scope.component && scope.field && scope.model) {
              scope.selectedOption = _.find(scope.options, function(o) { return o.value === scope.model; });
            }

            //if there is no current selection and a selection must be made
            if ((!scope.selectedOption) && (!scope.allowNoSelection)) {
              //select the first option
              scope.selectedOption = scope.options[0];
              scope.model = scope.selectedOption.value;
            }

            //if the selection changed as a result of updating the options
            if (scope.model !== previousSelection) {
              //ensure that the change handler is invoked
              scope.onChange();
            }
          }
        }

        scope.$watch('options', updateSelectedOption);
      }
    };
});
