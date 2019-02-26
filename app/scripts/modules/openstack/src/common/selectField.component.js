'use strict';

import _ from 'lodash';

const angular = require('angular');

import { InfrastructureCaches } from '@spinnaker/core';

SelectFieldController.$inject = ['$scope', '$element', '$attrs', '$timeout', '$q', '$rootScope', 'cacheInitializer'];
function SelectFieldController($scope, $element, $attrs, $timeout, $q, $rootScope, cacheInitializer) {
  var ctrl = this;
  var coveredThreshold = 0;

  this.refreshTooltipTemplate = require('./refresh.tooltip.html');

  function findOptionByValue(value) {
    return _.find(ctrl.options, function(o) {
      return angular.equals(o.value, value);
    });
  }

  //called whenever the list of options is updated to ensure that a default value is selected, if required
  function updateSelectedOption() {
    $scope.selectedOption = findOptionByValue(ctrl.value);
    if ($scope.selectedOption || ctrl.readOnly) {
      //a matching option is found - no need to set a default value
      return;
    }

    //No matching option or the selected option went away
    var previousSelection = ctrl.value;
    if (ctrl.allowNoSelection || ctrl.options.length == 0) {
      //No selection required or there are no options to choose from - leave as undefined
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

  function updateDone() {
    if (ctrl.backingCache && InfrastructureCaches.get(ctrl.backingCache)) {
      coveredThreshold = InfrastructureCaches.get(ctrl.backingCache).getStats().ageMax;
    }
    ctrl.updatingOptions = false;
  }

  function updateOptions() {
    ctrl.updatingOptions = true;
    ctrl.optionUpdate().then(updateDone, updateDone);
  }

  function clearRefreshingFlag() {
    ctrl.refreshing = false;
  }

  var stopWatchingRefreshTime = ctrl.backingCache
    ? $rootScope.$watch(
        function() {
          return InfrastructureCaches.get(ctrl.backingCache).getStats().ageMax;
        },
        function(ageMax) {
          if (ageMax) {
            ctrl.lastRefresh = ageMax;

            //update options, but don't start an infinite loop since fetching the options can also update ageMax
            if (!ctrl.updatingOptions && ageMax > coveredThreshold) {
              updateOptions();
            }
          }
        },
      )
    : angular.noop;

  this.refresh = function() {
    ctrl.refreshing = true;
    if (ctrl.backingCache) {
      cacheInitializer.refreshCache(ctrl.backingCache).then(clearRefreshingFlag, clearRefreshingFlag);
    } else {
      updateOptions();
    }
  };

  this.selectionUpdated = function() {
    if (ctrl.onChange) {
      ctrl.onChange({ value: ctrl.value });
    }
  };

  ctrl.$onInit = function() {
    $scope.showRefresh = !!ctrl.optionUpdate;
    _.defaults(ctrl, {
      noOptionsMessage: '(No options available)',
      noSelectionMessage: '(No selection)',
      labelColumnSize: 3,
      valueColumnSize: 7,
      readOnly: false,
      onChange: angular.noop,
      optionUpdate: function() {
        return $q.when(ctrl.options);
      },
    });

    updateOptions();
  };

  ctrl.$onChanges = function(changes) {
    if (changes['options']) {
      updateSelectedOption();
    }
  };

  ctrl.$onDestroy = function() {
    stopWatchingRefreshTime();
  };

  $scope.$on('updateOptions', updateOptions);
}

module.exports = angular.module('spinnaker.openstack.common.selectField', []).component('selectField', {
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
    noSelectionMessage: '@?',
    backingCache: '@?',
    optionUpdate: '<?',
  },
});
