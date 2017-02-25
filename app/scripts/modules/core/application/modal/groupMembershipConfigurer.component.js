'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.applicationModal.groupMembership.component', [
    require('../../config/settings.js')
  ])
  .component('groupMembershipConfigurer', {
    bindings: {
      requiredGroupMembership: '='
    },
    templateUrl: require('./groupMembershipConfigurer.component.html'),
    controller: function (settings, authenticationService) {

      this.availableRoles = authenticationService.getAuthenticatedUser().roles;

      this.fiatEnabled = settings.feature.fiatEnabled;
      if (!this.fiatEnabled) {
        return;
      }

      if (!this.requiredGroupMembership) {
        this.requiredGroupMembership = [];
      }
    }
  });
