'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('CloneServerGroupCtrl', function($scope, $modalInstance, accountService, orcaService, mortService,
                                               searchService, instanceTypeService, serverGroup, loadBalancers,
                                               securityGroups, subnets, accounts, packageImages, _) {
    $scope.healthCheckTypes = ['EC2', 'ELB'];
    $scope.terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];
    $scope.accounts = accounts;

    $scope.sgs = serverGroup.launchConfig.securityGroups;

    $scope.subnets = subnets;

    var populateRegionalSecurityGroups = function() {
      $scope.regionalSecurityGroups = securityGroups[$scope.command.credentials].aws[$scope.command.region];
    };


    $scope.command = {};
    if (serverGroup) {
      var asgNameRegex = /(\w+)(-v\d{3})?(-(\w+)?(-v\d{3})?(-(\w+))?)?(-v\d{3})?/;
      var match = asgNameRegex.exec(serverGroup.asg.autoScalingGroupName);
      $scope.command = {
        'application': match[1],
        'stack': match[4],
        'freeFormDetails': match[7],
        'credentials': serverGroup.account,
        'amiName': _(packageImages).find({'imageId': serverGroup.launchConfig.imageId}).imageName,
        'instanceType': serverGroup.launchConfig.instanceType,
        'iamRole': serverGroup.launchConfig.iamInstanceProfile,
        'keyPair': serverGroup.launchConfig.keyName,
        'associatePublicIpAddress': serverGroup.launchConfig.associatePublicIpAddress,
        'cooldown': serverGroup.asg.defaultCooldown,
        'healthCheckGracePeriod': serverGroup.asg.healthCheckGracePeriod,
        'healthCheckType': serverGroup.asg.healthCheckType,
        'terminationPolicies': serverGroup.asg.terminationPolicies,
        'ramdiskId': serverGroup.launchConfig.ramdiskId,
        'instanceMonitoring': serverGroup.launchConfig.instanceMonitoring.enabled,
        'ebsOptimized': serverGroup.launchConfig.ebsOptimized,
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
      var vpcZoneIdentifier = serverGroup.asg.vpczoneIdentifier;
      populateRegionalSecurityGroups();
      if (vpcZoneIdentifier !== '') {
        var subnetId = vpcZoneIdentifier.split(',')[0];
        $scope.command.subnetType = _($scope.subnets).find({'id': subnetId}).purpose;
      } else  {
        $scope.command.subnetType = '';
      }
      if (serverGroup.launchConfig.securityGroups.length) {
        if (serverGroup.launchConfig.securityGroups[0].indexOf('sg-') === 0) {
          $scope.command.securityGroups = _($scope.regionalSecurityGroups)
            .filter(function(item) {
              return _.contains(serverGroup.launchConfig.securityGroups, item.id);
            })
            .pluck('name')
            .valueOf();
        } else {
          $scope.command.securityGroups = serverGroup.launchConfig.securityGroups;
        }
      }
    }

    var populateRegionalImages = function() {
      $scope.images = _(packageImages)
        .filter({'region': $scope.command.region})
        .pluck('imageName')
        .valueOf();
    };
    populateRegionalImages();

    var populateRegionalAvailabilityZones = function() {
      $scope.regionalAvailabilityZones = _($scope.regions)
        .find({'name': $scope.command.region})
        .availabilityZones;
    };

    var populateRegions = function() {
      accountService.getRegionsForAccount($scope.command.credentials).then(function (result) {
        $scope.regions = result;
        populateRegionalAvailabilityZones();
      });
    };
    populateRegions();

    var populateRegionalSubnetPurposes = function() {
      $scope.regionSubnetPurposes = _(subnets)
        .filter({'account': $scope.command.credentials, 'region': $scope.command.region, 'target': 'ec2'})
        .pluck('purpose')
        .uniq()
        .union([''])
        .valueOf();
    };
    populateRegionalSubnetPurposes();

    var populateRegionalLoadBalancers = function() {
      $scope.regionalLoadBalancers = _(loadBalancers)
        .filter({'account': $scope.command.credentials, 'region': $scope.command.region})
        .pluck('loadBalancer')
        .valueOf();
    };
    populateRegionalLoadBalancers();

    var populateRegionalAvailableTypes = function() {
      instanceTypeService.getAvailableTypesForRegions([$scope.command.region]).then(function (result) {
        $scope.regionalInstanceTypes = result;
      });
    };
    populateRegionalAvailableTypes();

    $scope.$watch('command.credentials', function () {
      populateRegions();
      onRegionChange();
    });

    $scope.$watch('command.region', function () {
      onRegionChange();
      populateRegionalImages();
    });

    var onRegionChange = function() {
      populateRegionalSubnetPurposes();
      populateRegionalAvailabilityZones();
      populateRegionalLoadBalancers();
      populateRegionalSecurityGroups();
      populateRegionalAvailableTypes();
    };

    this.isValid = function () {
      return ($scope.command.amiName !== null) && ($scope.command.application !== null) &&
        ($scope.command.credentials !== null) && ($scope.command.instanceType !== null) &&
        ($scope.command.region !== null) && ($scope.command.availabilityZones !== null) &&
        ($scope.command.capacity.min !== null) && ($scope.command.capacity.max !== null) &&
        ($scope.command.capacity.desired !== null);
    };

    this.clone = function () {
      var command = angular.copy($scope.command);
      var availabilityZones = _.intersection(command.availabilityZones, $scope.regionalAvailabilityZones);
      var loadBalancers = _.intersection(command.loadBalancers, $scope.regionalLoadBalancers);
      var securityGroupNames = _.intersection(command.securityGroups, _.pluck($scope.regionalSecurityGroups, 'name'));
      command.availabilityZones = {};
      command.availabilityZones[command.region] = availabilityZones;
      command.loadBalancers = loadBalancers;
      command.securityGroups = securityGroupNames;
      $scope.sentCommand = command;
//      orcaService.cloneServerGroup(command)
//        .then(function (response) {
//          $modalInstance.close();
//          console.warn('task:', response.ref);
//        });
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
