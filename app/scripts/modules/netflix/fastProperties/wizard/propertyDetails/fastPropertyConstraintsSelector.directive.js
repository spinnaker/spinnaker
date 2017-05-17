'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.constraintSelector.directive', [])
  .directive('constraintsSelector', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyConstraintsSelector.directive.html'),
      scope: {},
      bindToController:{
        model: '=',
        isDisabled: '=',
      },
      controller: function($scope, $log) {
        let vm = this;

        vm.model = vm.model || 'none';

        let getConstraintByName = (name) => {
          return _.find(vm.constraints, {'name': name});
        };

        let extractConstraint = (fullConstraint = '') => {
          const split = fullConstraint.split(':');
          return split.length > 1 ? `${split[0]}` : fullConstraint;
        };

        let extractInput = (fullConstraint = '') => {
          const split = fullConstraint.split(':');
          return split.length > 1 ? split[1] : '';
        };

        vm.constraint = extractConstraint(vm.model.trim());
        vm.inputValue = extractInput(vm.model.trim());

        vm.constraints = [
          {name:'none'},
          {name:'int', placeholder:'ex: 3 or 100-200'},
          {name:'boolean'},
          {name:'range', placeholder:'ex: 100-  or 100-200'},
          {name:'min', placeholder: 'ex: 0.01'},
          {name:'max', placeholder: 'ex: 100'},
          {name:'length', placeholder: 'ex: 2-10'},
          {name:'json'},
          {name:'url', placeholder:'ex: {protocol:"http"}'},
          {name:'pattern', placeholder: 'INFO|WARN|ERROR'},
          {name:'email'}
        ];

        vm.isSelected = (constraintName) => {
          let s = constraintName === vm.constraint.trim();
          return s;
        };

        vm.onChange = () => {
          $log.debug('constraint change:', vm.constraint);
          vm.placeholder = getConstraintByName(vm.constraint).placeholder;
          vm.inputValue = '';
          vm.model = `${vm.constraint} ${vm.inputValue}`.trim();
        };

        vm.inputChanged = () => {
          vm.model = `${vm.constraint}${vm.inputValue.length > 0 ? ':' : ''} ${vm.inputValue}`;
        };

        vm.inputNeeded = () => {
          return _.has(getConstraintByName(vm.constraint), 'placeholder');
        };

        $scope.$watch(() => vm.model, () => {
          if(vm.model) {
            vm.constraint = extractConstraint(vm.model.trim());
            vm.inputValue = extractInput(vm.model.trim());
          }
        });

        return vm;
      },
      controllerAs: 'con',
    };
  });

