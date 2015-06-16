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
  .controller('CreateSecurityGroupCtrl', function($scope, $modalInstance, $state,
                                                  accountService, securityGroupReader, modalWizardService,
                                                  taskMonitorService, cacheInitializer, infrastructureCaches,
                                                  _, application, securityGroup, securityGroupWriter, vpcReader) {

    var ctrl = this;

    var allSecurityGroups = {};

    $scope.isNew = true;

    $scope.state = {
      submitting: false,
      refreshingSecurityGroups: false,
      removedRules: [],
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your security group',
      forceRefreshMessage: 'Getting your new security group from Amazon...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    $scope.securityGroup = securityGroup;

    function initializeSecurityGroups() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        allSecurityGroups = securityGroups;
      });
    }

    accountService.listAccounts('aws').then(function(accounts) {
      $scope.accounts = accounts;
      ctrl.accountUpdated();
    });

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

    this.getSecurityGroupRefreshTime = function() {
      return infrastructureCaches.securityGroups.getStats().ageMax;
    };

    this.refreshSecurityGroups = function() {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        return initializeSecurityGroups().then(function() {
          ctrl.vpcUpdated();
          $scope.state.refreshingSecurityGroups = false;
        });
      });
    };

    this.accountUpdated = function() {
      accountService.getRegionsForAccount($scope.securityGroup.credentials).then(function(regions) {
        $scope.regions = _.pluck(regions, 'name');
        clearSecurityGroups();
        ctrl.regionUpdated();
        ctrl.updateName();
      });
    };

    this.regionUpdated = function() {
      vpcReader.listVpcs().then(function(vpcs) {
        var vpcsByName = _.groupBy(vpcs, 'name');
        $scope.allVpcs = vpcs;
        var available = [];
        _.forOwn(vpcsByName, function(vpcsToTest, name) {
          var foundInAllRegions = true;
          _.forEach($scope.securityGroup.regions, function(region) {
            if (!_.some(vpcsToTest, { region: region, account: $scope.securityGroup.credentials })) {
              foundInAllRegions = false;
            }
          });
          if (foundInAllRegions) {
            available.push( {
              ids: _.pluck(vpcsToTest, 'id'),
              label: name,
            });
          }
        });
        $scope.vpcs = available;
        var match = _.find(available, function(vpc) {
          return vpc.ids.indexOf($scope.securityGroup.vpcId) !== -1;
        });
        $scope.securityGroup.vpcId = match ? match.ids[0] : null;
        ctrl.vpcUpdated();
      });
    };

    function configureFilteredSecurityGroups() {
      var vpcId = $scope.securityGroup.vpcId || null,
          account = $scope.securityGroup.credentials,
          regions = $scope.securityGroup.regions || [],
          existingSecurityGroupNames = [],
          availableSecurityGroups = [];

      regions.forEach(function (region) {
        var regionalVpcId = null;
        if (vpcId) {
          var baseVpc = _.find($scope.allVpcs, { id: vpcId });
          regionalVpcId = _.find($scope.allVpcs, { account: account, region: region, name: baseVpc.name }).id;
        }

        var regionalSecurityGroups = _.filter(allSecurityGroups[account].aws[region], { vpcId: regionalVpcId }),
          regionalGroupNames = _.pluck(regionalSecurityGroups, 'name');

        existingSecurityGroupNames = _.uniq(existingSecurityGroupNames.concat(regionalGroupNames));

        if (!availableSecurityGroups.length) {
          availableSecurityGroups = existingSecurityGroupNames;
        } else {
          availableSecurityGroups = _.intersection(availableSecurityGroups, regionalGroupNames);
        }

      });

      $scope.availableSecurityGroups = availableSecurityGroups;
      $scope.existingSecurityGroupNames = existingSecurityGroupNames;
      clearInvalidSecurityGroups();
    }

    function clearInvalidSecurityGroups() {
      var removed = $scope.state.removedRules;
      $scope.securityGroup.securityGroupIngress = $scope.securityGroup.securityGroupIngress.filter(function(rule) {
        if (rule.name && $scope.availableSecurityGroups.indexOf(rule.name) === -1) {
          removed.push(rule.name);
          return false;
        }
        return true;
      });
      if (removed.length) {
        modalWizardService.getWizard().markDirty('Ingress');
      }
    }

    this.vpcUpdated = function() {
      var account = $scope.securityGroup.credentials,
        regions = $scope.securityGroup.regions;
      if (account && regions && regions.length) {
        configureFilteredSecurityGroups();
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
      ruleset.push({
        type: 'tcp',
        startPort: 7001,
        endPort: 7001,
      });
    };

    this.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      $modalInstance.close();
      var newStateParams = {
        name: $scope.securityGroup.name,
        accountId: $scope.securityGroup.credentials,
        region: $scope.securityGroup.region,
        provider: 'aws',
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
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Create');
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };

    initializeSecurityGroups();
  });
