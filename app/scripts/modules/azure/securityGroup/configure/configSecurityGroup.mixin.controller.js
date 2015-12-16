'use strict';

var angular = require('angular');

module.exports = angular
  .module('spinnaker.azure.securityGroup.baseConfig.controller', [
    require('angular-ui-router'),
    require('../../../core/task/monitor/taskMonitorService.js'),
    require('../../../core/securityGroup/securityGroup.write.service.js'),
    require('../../../core/account/account.service.js'),
    require('../../vpc/vpc.read.service.js'),
    require('../../../core/modal/wizard/modalWizard.service.js'),
    require('../../../core/utils/lodash.js'),
  ])
  .controller('azureConfigSecurityGroupMixin', function ($scope,
                                                             $state,
                                                             $modalInstance,
                                                             taskMonitorService,
                                                             application,
                                                             securityGroup,
                                                             securityGroupReader,
                                                             securityGroupWriter,
                                                             accountService,
                                                             modalWizardService,
                                                             cacheInitializer,
                                                             vpcReader,
                                                             _ ) {



    var ctrl = this;

    $scope.isNew = true;

    $scope.state = {
      submitting: false,
      refreshingSecurityGroups: false,
      removedRules: [],
      infiniteScroll: {
        numToAdd: 20,
        currentItems: 20,
      },
    };

    ctrl.addMoreItems = function() {
      $scope.state.infiniteScroll.currentItems += $scope.state.infiniteScroll.numToAdd;
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your security group',
      forceRefreshMessage: 'Getting your new security group from Amazon...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    $scope.securityGroup = securityGroup;

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      // If the user has already closed the modal, do not navigate to the new details view
      if ($scope.$$destroyed) {
        return;
      }
      $modalInstance.close();
      var newStateParams = {
        name: $scope.securityGroup.name,
        accountId: $scope.securityGroup.credentials || $scope.securityGroup.accountName,
        region: $scope.securityGroup.regions[0],
        vpcId: $scope.securityGroup.vpcId,
        provider: 'azure',
      };
      if (!$state.includes('**.securityGroupDetails')) {
        $state.go('.securityGroupDetails', newStateParams);
      } else {
        $state.go('^.securityGroupDetails', newStateParams);
      }
    };

    ctrl.upsert = function () {
      $scope.taskMonitor.submit(
        function() {
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, 'Create');
        }
      );
    };

    function clearSecurityGroups() {
      $scope.availableSecurityGroups = [];
      $scope.existingSecurityGroupNames = [];
    }

    ctrl.accountUpdated = function() {
      var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
      accountService.getRegionsForAccount(account).then(function(regions) {
        $scope.regions = _.pluck(regions, 'name');
        clearSecurityGroups();
        ctrl.regionUpdated();
        ctrl.updateName();
      });
    };

    //ctrl.ifVcsFoundInAllRegions = function() {
    //  var foundInAllRegions = true;
    //  _.forEach($scope.securityGroup.regions, function(region) {
    //    if (!_.some(vpcsToTest, { region: region, account: $scope.securityGroup.credentials })) {
    //      foundInAllRegions = false;
    //    }
    //  });
    //  return foundInAllRegions;
    //};

    ctrl.regionUpdated = function() {
      var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
      vpcReader.listVpcs().then(function(vpcs) {
        var vpcsByName = _.groupBy(vpcs, 'label');
        $scope.allVpcs = vpcs;
        var available = [];
        _.forOwn(vpcsByName, function(vpcsToTest, label) {
          var foundInAllRegions = true;
          _.forEach($scope.securityGroup.regions, function(region) {
            if (!_.some(vpcsToTest, { region: region, account: account })) {
              foundInAllRegions = false;
            }
          });
          if (foundInAllRegions) {
            available.push( {
              ids: _.pluck(vpcsToTest, 'id'),
              label: label,
              deprecated: vpcsToTest[0].deprecated,
            });
          }
        });

        $scope.activeVpcs = available.filter(function(vpc) { return !vpc.deprecated; });
        $scope.deprecatedVpcs = available.filter(function(vpc) { return vpc.deprecated; });
        $scope.vpcs = available;

        var match = _.find(available, function(vpc) {
          return vpc.ids.indexOf($scope.securityGroup.vpcId) !== -1;
        });
        $scope.securityGroup.vpcId = match ? match.ids[0] : null;
        ctrl.vpcUpdated();
      });
    };

    this.vpcUpdated = function() {
      var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName,
        regions = $scope.securityGroup.regions;
      if (account && regions && regions.length) {
        configureFilteredSecurityGroups();
      } else {
        clearSecurityGroups();
      }
    };

    function configureFilteredSecurityGroups() {
      var vpcId = $scope.securityGroup.vpcId || null;
      var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
      var regions = $scope.securityGroup.regions || [];
      var existingSecurityGroupNames = [];
      var availableSecurityGroups = [];

      regions.forEach(function (region) {
        var regionalVpcId = null;
        if (vpcId) {
          var baseVpc = _.find($scope.allVpcs, { id: vpcId });
          regionalVpcId = _.find($scope.allVpcs, { account: account, region: region, name: baseVpc.name }).id;
        }

        var regionalSecurityGroups = _.filter(allSecurityGroups[account].azure[region], { vpcId: regionalVpcId }),
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

    ctrl.mixinUpsert = function (descriptor) {
      $scope.taskMonitor.submit(
        function() {
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, descriptor);
        }
      );
    };

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

    ctrl.refreshSecurityGroups = function() {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        return ctrl.initializeSecurityGroups().then(function() {
          ctrl.vpcUpdated();
          $scope.state.refreshingSecurityGroups = false;
        });
      });
    };

    var allSecurityGroups = {};

    ctrl.initializeSecurityGroups = function() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        allSecurityGroups = securityGroups;
        var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
        var region = $scope.securityGroup.regions[0];
        var vpcId = $scope.securityGroup.vpcId || null;

        var availableGroups;
        if(account && region) {
          availableGroups = _.filter(securityGroups[account].azure[region], { vpcId: vpcId });
        } else {
          availableGroups = securityGroups;
        }

        $scope.availableSecurityGroups = _.pluck(availableGroups, 'name');
      });
    };

    ctrl.cancel = function () {
      $modalInstance.dismiss();
    };

    ctrl.getCurrentNamePattern = function() {
      return $scope.securityGroup.vpcId ? vpcPattern : classicPattern;
    };

    ctrl.updateName = function() {
      var securityGroup = $scope.securityGroup,
        name = application.name;
      if (securityGroup.detail) {
        name += '-' + securityGroup.detail;
      }
      securityGroup.name = name;
      $scope.namePreview = name;
    };

    ctrl.namePattern = {
      test: function(name) {
        return ctrl.getCurrentNamePattern().test(name);
      }
    };

    ctrl.addRule = function(ruleset) {
      ruleset.push({
        type: 'tcp',
        startPort: 7001,
        endPort: 7001,
      });
    };

    ctrl.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    ctrl.dismissRemovedRules = function() {
       $scope.state.removedRules = [];
       modalWizardService.getWizard().markClean('Ingress');
       modalWizardService.getWizard().markComplete('Ingress');
    };

    var classicPattern = /^[\x00-\x7F]+$/;
    var vpcPattern = /^[a-zA-Z0-9\s._\-:\/()#,@[\]+=&;{}!$*]+$/;


  });

