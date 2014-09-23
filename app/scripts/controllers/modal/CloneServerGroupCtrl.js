'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('CloneServerGroupCtrl', function($scope, $modalInstance, _, $q,
                                               accountService, orcaService, mortService, oortService, searchService,
                                               instanceTypeService, modalWizardService, securityGroupService,
                                               serverGroup, application) {
    $scope.healthCheckTypes = ['EC2', 'ELB'];
    $scope.terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];

    $scope.state = {
      loaded: false
    };

    var accountLoader = accountService.getRegionsKeyedByAccount().then(function(regionsKeyedByAccount) {
      $scope.accounts = _.keys(regionsKeyedByAccount);
      $scope.regionsKeyedByAccount = regionsKeyedByAccount;
    });

    var securityGroupLoader = securityGroupService.getAllSecurityGroups().then(function(securityGroups) {
      $scope.securityGroups = securityGroups;
    });

    var loadBalancerLoader = oortService.listLoadBalancers().then(function(loadBalancers) {
      $scope.loadBalancers = loadBalancers;
    });

    var subnetLoader = mortService.listSubnets().then(function(subnets) {
      $scope.subnets = subnets;
    });

    var imageLoader = searchService.search({q: application.name, type: 'namedImages', pageSize: 100000000}).then(function(result) {
      $scope.packageImages = result.data[0].results;
    });

    $q.all([accountLoader, securityGroupLoader, loadBalancerLoader, subnetLoader, imageLoader]).then(function() {
      $scope.state.loaded = true;
      initializeCommand();
    });


    function initializeCommand() {
      if (serverGroup) {
        $scope.title = 'Clone ' + serverGroup.asg.autoScalingGroupName;
        var asgNameRegex = /(\w+)(-v\d{3})?(-(\w+)?(-v\d{3})?(-(\w+))?)?(-v\d{3})?/;
        var match = asgNameRegex.exec(serverGroup.asg.autoScalingGroupName);
        $scope.command = {
          'application': application.name,
          'stack': match[4],
          'freeFormDetails': match[7],
          'credentials': serverGroup.account,
          'cooldown': serverGroup.asg.defaultCooldown,
          'healthCheckGracePeriod': serverGroup.asg.healthCheckGracePeriod,
          'healthCheckType': serverGroup.asg.healthCheckType,
          'terminationPolicies': serverGroup.asg.terminationPolicies,
          'loadBalancers': serverGroup.asg.loadBalancerNames,
          'region': serverGroup.region,
          'availabilityZones': serverGroup.asg.availabilityZones,
          'capacity': {
            'min': serverGroup.asg.minSize,
            'max': serverGroup.asg.maxSize,
            'desired': serverGroup.asg.desiredCapacity
          },
          'source': {
            'account': serverGroup.account,
            'region': serverGroup.region,
            'asgName': serverGroup.asg.autoScalingGroupName
          }
        };
        if (serverGroup.launchConfig) {
          var amiName = null;
          if (serverGroup.launchConfig.imageId) {
            var foundImage = _($scope.packageImages).find({'imageId': serverGroup.launchConfig.imageId});
            if (foundImage) {
              amiName = foundImage.imageName;
            }
          }
          angular.extend($scope.command, {
            'instanceType': serverGroup.launchConfig.instanceType,
            'iamRole': serverGroup.launchConfig.iamInstanceProfile,
            'keyPair': serverGroup.launchConfig.keyName,
            'associatePublicIpAddress': serverGroup.launchConfig.associatePublicIpAddress,
            'ramdiskId': serverGroup.launchConfig.ramdiskId,
            'instanceMonitoring': serverGroup.launchConfig.instanceMonitoring.enabled,
            'ebsOptimized': serverGroup.launchConfig.ebsOptimized,
            'amiName': amiName
          });
        }
        var vpcZoneIdentifier = serverGroup.asg.vpczoneIdentifier;
        if (vpcZoneIdentifier !== '') {
          var subnetId = vpcZoneIdentifier.split(',')[0];
          var subnet = _($scope.subnets).find({'id': subnetId});
          $scope.command.subnetType = subnet.purpose;
          $scope.command.vpcId = subnet.vpcId;
        } else {
          $scope.command.subnetType = '';
          $scope.command.vpcId = null;
        }
        if (serverGroup.launchConfig && serverGroup.launchConfig.securityGroups.length) {
          $scope.command.securityGroups = serverGroup.launchConfig.securityGroups;
        }
      } else {
        $scope.title = 'Create ASG';
        $scope.command = {
          'application': application.name,
          'credentials': 'test',
          'region': 'us-east-1',
          'availabilityZones': [],
          'capacity': {
            'min': 0,
            'max': 0,
            'desired': 0
          },
          'cooldown': 10,
          'healthCheckType': 'EC2',
          'healthCheckGracePeriod': 600,
          'instanceMonitoring': false,
          'ebsOptimized': false,

          //These two should not be hard coded here, and keyPair options should be loaded from AWS
          'iamRole': 'BaseIAMRole',
          'keyPair': 'nf-test-keypair-a',

          'terminationPolicies': ['Default'],
          'source': {},
          'vpcId': null
        };
      }
    }

    this.isValid = function () {
      return ($scope.command.amiName !== null) && ($scope.command.application !== null) &&
        ($scope.command.credentials !== null) && ($scope.command.instanceType !== null) &&
        ($scope.command.region !== null) && ($scope.command.availabilityZones !== null) &&
        ($scope.command.capacity.min !== null) && ($scope.command.capacity.max !== null) &&
        ($scope.command.capacity.desired !== null);
    };

    this.clone = function () {
      var command = angular.copy($scope.command);
      command.amiName = _($scope.packageImages).find({'imageName': command.amiName}).imageId;
      command.availabilityZones = {};
      command.availabilityZones[command.region] = $scope.command.availabilityZones;
      $scope.sentCommand = command;
      orcaService.cloneServerGroup(command)
        .then(function (response) {
          $modalInstance.close();
          console.warn('task:', response.ref);
        });
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
