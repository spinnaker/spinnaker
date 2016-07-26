'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.widgets.numberInput.component', [])
  .component('numberInput', {
    template:`
      <div class="navbar-form" style="padding: 0 ;">
          <span class="btn-group btn-group-sm" role="group">
            <button type="button" class="btn btn-toggle btn-small" ng-click="$ctrl.toggle()" ng-class="{active: $ctrl.numberActive}" uib-tooltip="Toggle to enter number">Num</button>
            <button type="button" class="btn btn-toggle btn-small" ng-click="$ctrl.toggle()" ng-class="{active: $ctrl.expressionActive}" uib-tooltip="Toggle to enter expression"> $\{ </button>
          </span>
          <input ng-if="$ctrl.expressionActive" type="text" class="form-control input-sm" ng-model="$ctrl.model" style="width: 433px"/>
          <input ng-if="$ctrl.numberActive" type="number" class="form-control input-sm" ng-model="$ctrl.model" style="width: 75px"/>
      </div>
      `,
    bindings: {
      model: '=',
    },
    controller: function($scope) {
      let ctrl = this;
      let numberType = 'number';
      let textType = 'text';

      let setState = (inputType) => {
        if(inputType == textType) {
          ctrl.expressionActive = true;
          ctrl.numberActive = false;
        } else {
          ctrl.numberActive = true;
          ctrl.expressionActive = false;
        }
      };

      ctrl.$onInit = () => {
        ctrl.inputType = typeof ctrl.model === 'string' ? textType : numberType;
        setState(ctrl.inputType);
      };

      ctrl.toggle = () => {
        ctrl.inputType = ctrl.inputType === numberType ? textType : numberType;
        setState(ctrl.inputType);
        $scope.$digest();
      };
    }
  });
