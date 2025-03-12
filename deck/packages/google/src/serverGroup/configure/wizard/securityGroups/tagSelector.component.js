import { module } from 'angular';

import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE } from './tagManager.service';

('use strict');

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTOR_COMPONENT =
  'spinnaker.deck.gce.tagSelector.component';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTOR_COMPONENT; // for backwards compatibility
module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGSELECTOR_COMPONENT, [
  GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_SECURITYGROUPS_TAGMANAGER_SERVICE,
]).component('gceTagSelector', {
  bindings: {
    command: '=',
    securityGroupId: '=',
  },
  templateUrl: require('./tagSelector.component.html'),
  controller: [
    '$scope',
    'gceTagManager',
    function ($scope, gceTagManager) {
      this.securityGroup = gceTagManager.securityGroupObjectsKeyedById[this.securityGroupId];
      this.onSelect = gceTagManager.addTag;
      this.onRemove = gceTagManager.removeTag;

      $scope.$on('uis:select', function (event) {
        event.preventDefault();
      });
    },
  ],
});
