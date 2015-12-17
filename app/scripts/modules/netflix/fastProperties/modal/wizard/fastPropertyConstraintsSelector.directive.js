'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperties.constraintSelector.directive', [
    require('../../../../core/utils/lodash')
  ])
  .directive('constraintsSelector', () => {
    return {
      restrict: 'E',
      templateUrl: require('./fastPropertyConstraintsSelector.directive.html'),
      scope: {},
      bindToController:{
        model: '='
      },
      controller: function(_) {
        let vm = this;

        let getConstraintByName = (name) =>  {
          return _.find(vm.constraints, {'name': name});
        };

        let extractConstraint = (fullConstraint) => {
          const split = fullConstraint.split(":");
          return split.length > 1 ? `${split[0]}` : fullConstraint;
        };

        let extractInput = (fullConstraint) => {
          const split = fullConstraint.split(":");
          return split.length > 1 ? split[1] : '';
        };

        vm.constraint = extractConstraint(vm.model);
        vm.inputValue = extractInput(vm.model);

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

        vm.onChange = () => {
          console.log('constraint change:', vm.constraint);
          vm.placeholder = getConstraintByName(vm.constraint).placeholder;
          vm.inputValue = '';
          vm.model = `${vm.constraint} ${vm.inputValue}`;
        };

        vm.inputChanged = () => {
          vm.model = `${vm.constraint}${vm.inputValue.length > 0 ? ':' : ''} ${vm.inputValue}`;
        };

        vm.inputNeeded = () => {
          return _.has(getConstraintByName(vm.constraint), 'placeholder');
        };


        return vm;
      },
      controllerAs: 'con',
    };
  });

