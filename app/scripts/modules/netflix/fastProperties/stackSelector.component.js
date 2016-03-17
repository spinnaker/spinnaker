'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastproperty.stackSelector.component', [])
  .component('stackSelector', {
    bindings: {
      model: '=',
      stacks: '=',
      onChange: '&',
    },
    templateUrl: require('./stackSelector.component.html'),
    controllerAs: 'fp',
    controller: function controller() {
      var vm = this;

      vm.stackChanged = () => {
        vm.onChange({stack: vm.model});
      };
    },
  });
