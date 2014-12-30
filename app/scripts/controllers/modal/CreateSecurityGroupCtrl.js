'use strict';


angular.module('deckApp')
  .controller('CreateSecurityGroupCtrl', function($scope, $modalInstance, $exceptionHandler, $state,
                                                  accountService, securityGroupService,
                                                  taskMonitorService,
                                                  _, application, securityGroup, securityGroupWriter, vpcReader) {

    var ctrl = this;

    var allSecurityGroups = {};

    $scope.isNew = true;

    $scope.state = {
      submitting: false
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your security group',
      forceRefreshMessage: 'Getting your new security group from Amazon...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    $scope.securityGroup = securityGroup;

    securityGroupService.getAllSecurityGroups().then(function(securityGroups) {
      allSecurityGroups = securityGroups;
    });

    accountService.listAccounts().then(function(accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

    this.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.securityGroup.credentials).then(function(regions) {
        $scope.regions = regions;
        clearSecurityGroups();
        ctrl.regionUpdated();
        ctrl.updateName();
      });
    };

    this.regionUpdated = function() {
      vpcReader.listVpcs().then(function(vpcs) {
        var account = $scope.securityGroup.credentials,
            region = $scope.securityGroup.region,
            availableVpcs = _(vpcs)
              .filter({ account: account, region: region })
              .map(function(vpc) {
                return {
                  id: vpc.id,
                  label: (vpc.name || '[No Name] ') + ' (' + vpc.id + ')'
                };
              })
              .value();
        $scope.vpcs = availableVpcs;
        $scope.securityGroup.vpcId = null;
        ctrl.vpcUpdated();
      });
    };

    this.vpcUpdated = function() {
      var account = $scope.securityGroup.credentials,
        region = $scope.securityGroup.region,
        vpcId = $scope.securityGroup.vpcId || null;
      if (account && region && allSecurityGroups[account] && allSecurityGroups[account].aws[region]) {
        $scope.availableSecurityGroups = _.filter(allSecurityGroups[account].aws[region], { vpcId: vpcId });
        $scope.existingSecurityGroupNames = _.collect($scope.availableSecurityGroups, 'name');
      } else {
        clearSecurityGroups();
      }
    };

    var classicPattern = /^[\x00-\x7F]+$/,
      vpcPattern = /^[a-zA-Z0-9\s._\-:\/()#,@[\]+=&;{}!$*]+$/;

    this.getCurrentNamePattern = function() {
      return $scope.securityGroup.vpc ? vpcPattern : classicPattern;
    };

    this.updateName = function() {
      var securityGroup = $scope.securityGroup,
        name = application.name;
      if (securityGroup.detail) {
        name += '-' + securityGroup.detail;
      }
      securityGroup.name = name;
      $scope.namePreview = name;
    };

    this.namePattern = (function() {
      return {
        test: function(name) {
          return ctrl.getCurrentNamePattern().test(name);
        }
      };
    })();

    this.addRule = function(ruleset) {
      ruleset.push({});
    };

    this.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      $modalInstance.close();
      var newStateParams = {
        name: $scope.securityGroup.name,
        accountId: $scope.securityGroup.credentials,
        region: $scope.securityGroup.region
      };
      if (!$state.includes('**.securityGroupDetails')) {
        $state.go('.securityGroupDetails', newStateParams);
      } else {
        $state.go('^.securityGroupDetails', newStateParams);
      }
    };

    this.upsert = function () {
      $scope.taskMonitor.submit(
        function() {
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application.name, 'Create');
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
