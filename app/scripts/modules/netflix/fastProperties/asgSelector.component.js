'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastproperty.asgSelector.component', [])
  .component('asgSelector', {
    bindings: {
      model: '=',
      asgs: '=',
      onChange: '&'
    },
    templateUrl: require('./asgSelector.component.html'),
    controllerAs: 'fp',
    controller: function controller() {
      var vm = this;
      vm.freeFormAsgField = true;

      vm.toggleFreeFormAsgField = function() {
        vm.freeFormAsgField = !vm.freeFormAsgField;
      };

      vm.asgChanged = () => {
        vm.onChange({asg: vm.model});
      };

    },
  });
