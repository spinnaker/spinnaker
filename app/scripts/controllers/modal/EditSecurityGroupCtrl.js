'use strict';


angular.module('deckApp')
  .controller('EditSecurityGroupCtrl', function($scope, $modalInstance, $exceptionHandler, $state,
                                                accountService,  securityGroupService,
                                                taskMonitorService,
                                                _, application, securityGroup, securityGroupWriter) {

    $scope.securityGroup = securityGroup;

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Updating your security group',
      forceRefreshMessage: 'Getting your updated security group from Amazon...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

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

    $scope.taskMonitor.onApplicationRefresh = $modalInstance.dismiss;

    this.upsert = function () {

      $scope.taskMonitor.submit(
        function() {
          return securityGroupWriter.upsertSecurityGroup($scope.securityGroup, application.name, 'Update');
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
