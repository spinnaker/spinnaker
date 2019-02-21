'use strict';

const angular = require('angular');

// TODO(dpeach): this approach is unmaintainable because we
// have to intercept ui-select event emitters to make it work.
module.exports = angular
  .module('spinnaker.deck.gce.backendServiceSelector.component', [])
  .component('gceBackendServiceSelector', {
    bindings: {
      command: '=',
      loadBalancerName: '=',
    },
    templateUrl: require('./backendServiceSelector.component.html'),
    controller: [
      '$scope',
      function($scope) {
        $scope.$on('$destroy', () => {
          if (this.command.backendServices) {
            delete this.command.backendServices[this.loadBalancerName];
          }
        });

        $scope.$on('uis:select', function(event) {
          event.preventDefault();
        });
      },
    ],
  });
