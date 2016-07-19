'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.openstack.common.selectField', [
  require('../../core/utils/lodash'),
]).component('selectField', {
  templateUrl: require('./selectField.component.html'),
  controller: SelectFieldController,
  bindings: {
      label: '@',
      options: '<',
      value: '<?',
      onChange: '&',
      labelColumnSize: '@?',
      valueColumnSize: '@?',
      helpKey: '@?',
      readOnly: '<?',
      allowNoSelection: '<?',
      noOptionsMessage: '@?',
      noSelectionMessage: '@?'
  }
});

function SelectFieldController($scope, $element, $attrs, _) {
  var ctrl = this;

  function findOptionByValue(value) {
    _.find(ctrl.options, function(o) { return o.value === value; });
  }

  //called whenever the list of options is updated to ensure that a default value is selected, if required
  function updateSelectedOption() {
    $scope.selectedOption = findOptionByValue(ctrl.value);
    if ($scope.selectedOption) {
      //a matching option is found - no need to set a default value
      return;
    }

    //No matching option or the selected option went away
    var previousSelection = ctrl.value;
    if (ctrl.allowNoSelection || ctrl.readOnly || ctrl.options.length == 0) {
      //No selection required, the field is read only, or there are no options to choose from - leave as undefined
      ctrl.value = undefined;
    } else {
      //set to the first value in the list
      $scope.selectedOption = ctrl.options[0];
      ctrl.value = ctrl.options[0].value;
    }

    if (previousSelection !== ctrl.value) {
      ctrl.selectionUpdated();
    }
  }

  ctrl.$onInit = function() {
    _.defaults(ctrl, {
      noOptionsMessage: '(No options available)',
      noSelectionMessage: '(No selection)',
      labelColumnSize: 3,
      valueColumnSize: 7,
      readOnly: false,
      onChange: angular.noop
    });

    updateSelectedOption();
  };

  ctrl.$onChanges = function(changes) {
    if (changes['options']) {
      updateSelectedOption();
    }
  };

  this.selectionUpdated = function() {
    if (ctrl.onChange) {
      ctrl.onChange({value: ctrl.value});
    }
  };
}
