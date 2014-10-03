'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('EditSecurityGroupCtrl', function($scope, $modalInstance, $exceptionHandler,
                                                accountService, orcaService, securityGroupService, mortService,
                                                _, application, securityGroup) {

    $scope.securityGroup = securityGroup;

    securityGroup.securityGroupIngress = _(securityGroup.inboundRules)
      .filter(function(rule) {
        return rule.securityGroup;
      }).map(function(rule) {
        return rule.portRanges.map(function(portRange) {
          return {
            name: rule.securityGroup.name,
            type: rule.protocol,
            startPort: portRange.startPort,
            endPort: portRange.endPort
          };
        });
      })
      .flatten()
      .value();

    securityGroupService.getAllSecurityGroups().then(function(securityGroups) {
      var account = securityGroup.accountName,
          region = securityGroup.region,
          vpcId = securityGroup.vpcId || null;
      $scope.availableSecurityGroups = _.filter(securityGroups[account].aws[region], { vpcId: vpcId });
    });

    this.addRule = function(ruleset) {
      ruleset.push({});
    };

    this.removeRule = function(ruleset, index) {
      ruleset.splice(index, 1);
    };

    this.upsert = function () {
      orcaService.upsertSecurityGroup($scope.securityGroup, application.name, 'Update')
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
