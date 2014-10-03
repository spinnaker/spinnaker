'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('CreateSecurityGroupCtrl', function($scope, $modalInstance, $exceptionHandler,
                                                  accountService, orcaService, securityGroupService, mortService,
                                                  _, application, securityGroup) {

    var ctrl = this;

    var allSecurityGroups = {};

    $scope.isNew = true;

    $scope.state = {
      submitting: false
    };

    $scope.taskStatus = {
      errorMessage: null,
      lastStage: null,
      hideProgressMessage: false,
      taskId: null,
      applicationName: application.name
    };

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
      mortService.listVpcs().then(function(vpcs) {
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

    this.upsert = function () {
      $scope.state.submitting = true;
      $scope.taskStatus.errorMessage = null;

      orcaService.upsertSecurityGroup($scope.securityGroup, application.name, 'Create')
        .then(function (task) {
          $scope.taskStatus.taskId = task.id;
          task.watchForKatoCompletion().then(
            function() { // kato succeeded
              $modalInstance.close();
              task.watchForForceRefresh().then(
                function() { // cache has been refreshed; object should be available
                  application.refreshImmediately();
                },
                function(task) { // cache refresh never happened?
                  $exceptionHandler('task failed to force cache refresh:', task);
                }
              );
            },
            function(updatedTask) { // kato failed
              $scope.state.submitting = false;
              $scope.taskStatus.errorMessage = updatedTask.statusMessage || 'There was an unknown server error.';
              $scope.taskStatus.lastStage = null;
            },
            function(notification) {
              $scope.taskStatus.lastStage = notification;
            }
          );
        },
        function(error) {
          $scope.state.submitting = false;
          $scope.taskStatus.errorMessage = error.message || 'There was an unknown server error.';
          $scope.taskStatus.lastStage = null;
          $exceptionHandler('Post to pond failed:', error);
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
