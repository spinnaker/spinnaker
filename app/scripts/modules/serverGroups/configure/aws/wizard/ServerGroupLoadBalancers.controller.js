'use strict';


angular.module('deckApp.serverGroup.configure.aws')
  .controller('awsServerGroupLoadBalancersCtrl', function(modalWizardService) {
    modalWizardService.getWizard().markClean('load-balancers');
    modalWizardService.getWizard().markComplete('load-balancers');

  });
