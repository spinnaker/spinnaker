'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.widgets.numberInput.component', [])
  .component('numberInput', {
    template:`
      <div class="navbar-form" style="padding: 0 ;">
        <div class="button-input" ng-class="{number: $ctrl.numberActive, text: $ctrl.textActive, focus: $ctrl.isGlowing}">
          <span class="btn-group btn-group-xs" role="group">
            <button type="button"
              class="btn btn-default"
              ng-click="$ctrl.toggleNum()"
              ng-class="{active: $ctrl.numberActive}"
              ng-focus="$ctrl.glow(true)"
              ng-blur="$ctrl.glow(false)"
              uib-tooltip="Toggle to enter number">
              Num
              </button>
            <button type="button"
              class="btn btn-default"
              ng-click="$ctrl.toggleText()"
              ng-class="{active: $ctrl.expressionActive}"
              ng-focus="$ctrl.glow(true)"
              ng-blur="$ctrl.glow(false)"
              uib-tooltip="Toggle to enter expression">
              $\{…\}
              </button>
          </span>
          <input
            ng-if="$ctrl.expressionActive"
            type="text" class="form-control borderless"
            ng-model="$ctrl.model" ng-focus="$ctrl.glow(true)"
            ng-blur="$ctrl.glow(false)"
          />
          <input
            ng-if="$ctrl.numberActive"
            type="number"
            class="form-control borderless"
            ng-model="$ctrl.model"
            ng-focus="$ctrl.glow(true)"
            ng-blur="$ctrl.glow(false)"
          />
        </div>
      </div>
      `,
    bindings: {
      model: '=',
    },
    controller: function() {
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

      ctrl.toggleNum = () => {
        setState(numberType);
      };

      ctrl.toggleText = () => {
        setState(textType);
      };

      ctrl.glow = (isGlowing) => {
        ctrl.isGlowing = isGlowing;
      };
    }
  });
