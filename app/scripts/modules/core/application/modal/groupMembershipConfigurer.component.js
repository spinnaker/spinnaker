'use strict';
import {SETTINGS} from 'core/config/settings';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.applicationModal.groupMembership.component', [])
  .component('groupMembershipConfigurer', {
    bindings: {
      requiredGroupMembership: '='
    },
    templateUrl: require('./groupMembershipConfigurer.component.html'),
    controller: function (authenticationService) {

      this.availableRoles = authenticationService.getAuthenticatedUser().roles;

      this.fiatEnabled = SETTINGS.feature.fiatEnabled;
      if (!this.fiatEnabled) {
        return;
      }

      if (!this.requiredGroupMembership) {
        this.requiredGroupMembership = [];
      }
    }
  });
