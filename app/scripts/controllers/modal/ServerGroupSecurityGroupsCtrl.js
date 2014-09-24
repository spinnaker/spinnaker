'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ServerGroupSecurityGroupsCtrl', function(modalWizardService) {
    modalWizardService.getWizard().markClean('security-groups');
    modalWizardService.getWizard().markComplete('security-groups');
  });
