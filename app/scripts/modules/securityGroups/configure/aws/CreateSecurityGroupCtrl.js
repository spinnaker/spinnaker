'use strict';


angular.module('spinnaker.securityGroup.aws.create.controller', [
  'ui.router',
  'spinnaker.account.service',
  'spinnaker.caches.infrastructure',
  'spinnaker.caches.initializer',
  'spinnaker.tasks.monitor.service',
  'spinnaker.securityGroup.write.service',
  'spinnaker.vpc.read.service',
  'spinnaker.modalWizard',
])
  .controller('CreateSecurityGroupCtrl', function($scope, $modalInstance, $state, $controller,
                                                  accountService, securityGroupReader, modalWizardService,
                                                  taskMonitorService, cacheInitializer, infrastructureCaches,
                                                  _, application, securityGroup ) {

    var ctrl = this;

    angular.extend(this, $controller('ConfigSecurityGroupMixin', {
      $scope: $scope,
      $modalInstance: $modalInstance,
      application: application,
      securityGroup: securityGroup,
    }));


    accountService.listAccounts('aws').then(function(accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });

    this.getSecurityGroupRefreshTime = function() {
      return infrastructureCaches.securityGroups.getStats().ageMax;
    };


    ctrl.upsert = function () {
      ctrl.mixinUpsert('Create');
    };

    ctrl.initializeSecurityGroups();

  });
