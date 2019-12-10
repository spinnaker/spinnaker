'use strict';

const angular = require('angular');

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTOR_COMPONENT =
  'spinnaker.deck.gce.tagSelector.component';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTOR_COMPONENT; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTOR_COMPONENT, [
    require('./tagManager.service').name,
  ])
  .component('gceTagSelector', {
    bindings: {
      command: '=',
      securityGroupId: '=',
    },
    templateUrl: require('./tagSelector.component.html'),
    controller: [
      '$scope',
      'gceTagManager',
      function($scope, gceTagManager) {
        this.securityGroup = gceTagManager.securityGroupObjectsKeyedById[this.securityGroupId];
        this.onSelect = gceTagManager.addTag;
        this.onRemove = gceTagManager.removeTag;

        $scope.$on('uis:select', function(event) {
          event.preventDefault();
        });
      },
    ],
  });
