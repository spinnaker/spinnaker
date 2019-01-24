'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.deck.gce.tagSelector.component', [require('./tagManager.service').name])
  .component('gceTagSelector', {
    bindings: {
      command: '=',
      securityGroupId: '=',
    },
    templateUrl: require('./tagSelector.component.html'),
    controller: function($scope, gceTagManager) {
      this.securityGroup = gceTagManager.securityGroupObjectsKeyedById[this.securityGroupId];
      this.onSelect = gceTagManager.addTag;
      this.onRemove = gceTagManager.removeTag;

      $scope.$on('uis:select', function(event) {
        event.preventDefault();
      });
    },
  });
