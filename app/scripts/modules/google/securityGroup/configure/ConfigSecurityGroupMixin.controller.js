'use strict';

var angular = require('angular');

module.exports = angular
  .module('spinnaker.google.securityGroup.baseConfig.controller', [
    require('angular-ui-router'),
    require('../../../core/task/monitor/taskMonitorService.js'),
    require('../../../core/securityGroup/securityGroup.write.service.js'),
    require('../../../core/account/account.service.js'),
    require('../../../core/modal/wizard/modalWizard.service.js'),
    require('../../../core/network/network.read.service.js'),
    require('../../../core/utils/lodash.js'),
  ])
  .controller('gceConfigSecurityGroupMixin', function ($scope,
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
                                                       networkReader,
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
      forceRefreshMessage: 'Getting your new security group from Google...',
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
        region: 'global',
        provider: 'gce',
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

    ctrl.mixinUpsert = function (descriptor) {
      $scope.taskMonitor.submit(
        function() {
          var allowed = _.map($scope.securityGroup.ipIngress, function(ipIngressRule) {
            var rule = {
              ipProtocol: ipIngressRule.type,
            };

            if (ipIngressRule.startPort && ipIngressRule.endPort) {
              rule.portRanges = [ipIngressRule.startPort + '-' + ipIngressRule.endPort];
            }

            return rule;
          });

          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application, descriptor, {
            cloudProvider: 'gce',
            providerType: 'gce',
            securityGroupName: $scope.securityGroup.name,
            sourceRanges: _.uniq(_.pluck($scope.securityGroup.sourceRanges, 'value')),
            allowed: allowed,
            region: "global",
            network: $scope.securityGroup.network,
          });
        }
      );
    };

    ctrl.accountUpdated = function() {
      ctrl.initializeSecurityGroups();
      ctrl.updateNetworks();
      ctrl.updateName();
    };

    ctrl.refreshSecurityGroups = function() {
      $scope.state.refreshingSecurityGroups = true;
      return cacheInitializer.refreshCache('securityGroups').then(function() {
        return ctrl.initializeSecurityGroups().then(function() {
          $scope.state.refreshingSecurityGroups = false;
        });
      });
    };

    var allSecurityGroups = {};

    ctrl.initializeSecurityGroups = function() {
      return securityGroupReader.getAllSecurityGroups().then(function (securityGroups) {
        allSecurityGroups = securityGroups;
        var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;

        var existingGroups;
        if(account) {
          existingGroups = securityGroups[account].gce.global;
        } else {
          existingGroups = securityGroups;
        }

        $scope.existingSecurityGroupNames = _.pluck(existingGroups, 'name');
      });
    };

    ctrl.cancel = function() {
      $modalInstance.dismiss();
    };

    ctrl.updateNetworks = function() {
      networkReader.listNetworksByProvider('gce').then(function(gceNetworks) {
        var account = $scope.securityGroup.credentials || $scope.securityGroup.accountName;
        $scope.securityGroup.backingData.networks = _.pluck(_.filter(gceNetworks, { account: account }), 'name');
      });
    };

    ctrl.getCurrentNamePattern = function() {
      return /.+/;
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

    ctrl.addSourceCIDR = function(sourceRanges) {
      sourceRanges.push({value: '0.0.0.0/0'});
    };

    ctrl.removeSourceCIDR = function(sourceRanges, index) {
      sourceRanges.splice(index, 1);
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

  });

