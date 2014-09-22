'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('CloneServerGroupCtrl', function($scope, $modalInstance, accountService, orcaService, mortService,
                                               searchService, instanceTypeService, modalWizardService,serverGroup,
                                               loadBalancers, securityGroups, subnets, regionsKeyedByAccount,
                                               packageImages, application, $, _) {
    $scope.healthCheckTypes = ['EC2', 'ELB'];
    $scope.terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];
    $scope.accounts = _.keys(regionsKeyedByAccount);

    if (serverGroup) {
      $scope.title = 'Clone ' + serverGroup.asg.autoScalingGroupName;
      var asgNameRegex = /(\w+)(-v\d{3})?(-(\w+)?(-v\d{3})?(-(\w+))?)?(-v\d{3})?/;
      var match = asgNameRegex.exec(serverGroup.asg.autoScalingGroupName);
      $scope.command = {
        'application': application.name,
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
      if (vpcZoneIdentifier !== '') {
        var subnetId = vpcZoneIdentifier.split(',')[0];
        var subnet = _(subnets).find({'id': subnetId}).purpose;
        $scope.command.subnetType = subnet.purpose;
        $scope.command.vpcId = subnet.vpcId;
      } else  {
        $scope.command.subnetType = '';
        $scope.command.vpcId = null;
      }
      if (serverGroup.launchConfig.securityGroups.length) {
        if (serverGroup.launchConfig.securityGroups[0].indexOf('sg-') === 0) {
          $scope.command.securityGroups = _(securityGroups[$scope.command.credentials].aws[$scope.command.region])
            .filter(function(item) {
              return _.contains(serverGroup.launchConfig.securityGroups, item.id);
            })
            .pluck('name')
            .valueOf();
        } else {
          $scope.command.securityGroups = serverGroup.launchConfig.securityGroups;
        }
      }
    } else {
      $scope.title = 'Create ASG';
      $scope.command = {
        'application': application.name,
        'credentials': 'test',
        'region': 'us-east-1',
        'capacity': {},
        'source': {}
      };
    }

    var populateRegionalSecurityGroups = function() {
      $scope.regionalSecurityGroups = _(securityGroups[$scope.command.credentials].aws[$scope.command.region])
        .filter({'vpcId': $scope.command.vpcId})
        .pluck('name')
        .valueOf();
    };
    populateRegionalSecurityGroups();

    var populateRegions = function() {
      $scope.regions = regionsKeyedByAccount[$scope.command.credentials].regions;
    };
    populateRegions();

    var populateRegionalAvailabilityZones = function() {
      $scope.regionalAvailabilityZones = _.find(regionsKeyedByAccount[$scope.command.credentials].regions, {'name': $scope.command.region}).availabilityZones;
    };
    populateRegionalAvailabilityZones();

    var populateRegionalImages = function() {
      $scope.images = _(packageImages)
        .filter({'region': $scope.command.region})
        .valueOf();
    };
    populateRegionalImages();

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
      $scope.regionalLoadBalancers  = _(loadBalancers)
        .pluck('accounts')
        .flatten(true)
        .filter({'name': $scope.command.credentials})
        .pluck('regions')
        .flatten(true)
        .filter({'name': $scope.command.region})
        .pluck('loadBalancers')
        .flatten(true)
        .pluck('elb')
        .remove(undefined)
        .filter({'vpcid': $scope.command.vpcId})
        .pluck('loadBalancerName')
        .unique()
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

    $scope.$watch('command.subnetType', function () {
      var subnet = _(subnets)
        .find({'purpose': $scope.command.subnetType, 'availabilityZone': $scope.command.availabilityZones[0]});
      $scope.command.vpcId = subnet ? subnet.vpcId : null;
      populateRegionalLoadBalancers();
      populateRegionalSecurityGroups();
    });

    var onRegionChange = function() {
      populateRegionalAvailabilityZones();
      populateRegionalSubnetPurposes();
      populateRegionalLoadBalancers();
      populateRegionalSecurityGroups();
      populateRegionalAvailableTypes();
    };

    instanceTypeService.getCategories().then(function(categories) {
      $scope.instanceProfiles = categories;
    });

    this.selectInstanceType = function (type, $event) {
      if ($event.target && $($event.target).is('select, a')) {
        return;
      }
      if ($scope.command.instanceProfile === type) {
        type = null;
      }
      $scope.command.instanceProfile = type;
      if (type === 'custom') {
        modalWizardService.getWizard().excludePage('Instance Type');
      } else {
        modalWizardService.getWizard().includePage('Instance Type');
        $scope.instanceProfiles.forEach(function(profile) {
          if (profile.type === type) {
            $scope.selectedInstanceProfile = profile;
          }
        });
      }
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
      command.amiName = _($scope.images).find({'imageName': command.amiName}).imageId;
      command.availabilityZones = {};
      command.availabilityZones[command.region] = availabilityZones;
      command.loadBalancers = loadBalancers;
      command.securityGroups = securityGroupNames;
      $scope.sentCommand = command;
      orcaService.cloneServerGroup(command)
        .then(function (response) {
          $modalInstance.close();
          console.warn('task:', response.ref);
        });
      $modalInstance.close(command);
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
