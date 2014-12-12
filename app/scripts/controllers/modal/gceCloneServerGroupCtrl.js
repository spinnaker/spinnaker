'use strict';


angular.module('deckApp.gce')
  .controller('gceCloneServerGroupCtrl', function($scope, $modalInstance, _, $q, $exceptionHandler, $state,
                                                  accountService, orcaService, mortService, oortService,
                                                  instanceTypeService, modalWizardService, securityGroupService, taskMonitorService,
                                                  imageService, serverGroupCommand, application, title) {
    $scope.title = title;
    $scope.healthCheckTypes = ['EC2', 'ELB'];
    $scope.terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];

    $scope.applicationName = application.name;

    $scope.state = {
      loaded: false,
      imagesLoaded: false
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your server group',
      forceRefreshMessage: 'Getting your new server group from Google...',
      modalInstance: $modalInstance,
      forceRefreshEnabled: true
    });

    var accountLoader = accountService.getRegionsKeyedByAccount().then(function(regionsKeyedByAccount) {
      $scope.accounts = _.keys(regionsKeyedByAccount);
      $scope.regionsKeyedByAccount = regionsKeyedByAccount;
    });

    var securityGroupLoader = securityGroupService.getAllSecurityGroups().then(function(securityGroups) {
      $scope.securityGroups = securityGroups;
    });

    var loadBalancerLoader = oortService.listGCELoadBalancers().then(function(loadBalancers) {
      $scope.loadBalancers = loadBalancers;
    });

    var subnetLoader = mortService.listSubnets().then(function(subnets) {
      $scope.subnets = subnets;
    });

    $scope.command = serverGroupCommand;

    var imageLoader = imageService.findImages($scope.command.selectedProvider, application.name, serverGroupCommand.region, serverGroupCommand.credentials).then(function(images) {
      $scope.gceImages = images;
      $scope.lastImageAccount = serverGroupCommand.credentials;
    });

    var instanceTypeLoader = instanceTypeService.getAllTypesByRegion($scope.command.selectedProvider).then(function(instanceTypes) {
      $scope.instanceTypesByRegion = instanceTypes;
    });

    $q.all([accountLoader, securityGroupLoader, loadBalancerLoader, subnetLoader, imageLoader, instanceTypeLoader]).then(function() {
      $scope.state.loaded = true;
      initializeCommand();
      initializeWizardState();
      initializeSelectOptions();
      initializeWatches();
    });

    function initializeWizardState() {
      if (serverGroupCommand.viewState.mode === 'clone') {
        if ($scope.command.image) {
          modalWizardService.getWizard().markComplete('location');
        }
        modalWizardService.getWizard().markComplete('load-balancers');
        //modalWizardService.getWizard().markComplete('security-groups');
        modalWizardService.getWizard().markComplete('instance-profile');
        modalWizardService.getWizard().markComplete('instance-type');
        modalWizardService.getWizard().markComplete('capacity');
        modalWizardService.getWizard().markComplete('advanced');
      }
    }

    function initializeWatches() {
      $scope.$watch('command.credentials', credentialsChanged);
      $scope.$watch('command.region', regionChanged);
      $scope.$watch('command.subnetType', subnetChanged);
    }

    function initializeSelectOptions() {
      credentialsChanged();
      regionChanged();
      configureSubnetPurposes();
      configureSecurityGroupOptions();
    }

    function credentialsChanged() {
      if ($scope.command.credentials) {
        $scope.regionToZonesMap = $scope.regionsKeyedByAccount[$scope.command.credentials].regions;

        if (!$scope.regionToZonesMap) {
          // TODO(duftler): Move these default values to settings.js and/or accountService.js.
          $scope.regionToZonesMap = {
            'us-central1': ['us-central1-a', 'us-central1-b', 'us-central1-f'],
            'europe-west1': ['europe-west1-a', 'europe-west1-b', 'europe-west1-c'],
            'asia-east1': ['asia-east1-a', 'asia-east1-b', 'asia-east1-c']
          };
        }

        $scope.regions = _.keys($scope.regionToZonesMap);

        if ($scope.regions.indexOf($scope.command.region) === -1) {
          $scope.command.region = null;
        }
      } else {
        $scope.command.region = null;
      }

      configureImages();
    }

    function regionChanged() {
      if ($scope.regionToZonesMap) {
        $scope.zones = $scope.regionToZonesMap[$scope.command.region];

        if (!$scope.command.region || $scope.zones.indexOf($scope.command.zone) === -1) {
          $scope.command.zone = null;
        }
      } else {
        $scope.zones = null;
        $scope.command.zone = null;
      }

      configureLoadBalancerOptions();
    }

    function subnetChanged() {
      if (true) { return; }

      var subnet = _($scope.subnets)
        .find({purpose: $scope.command.subnetType, account: $scope.command.credentials, region: $scope.command.region});
      $scope.command.vpcId = subnet ? subnet.vpcId : null;
      configureSecurityGroupOptions();
      configureLoadBalancerOptions();
    }

    function configureSubnetPurposes() {
      if (true) { return; }

      if ($scope.command.region === null) {
        $scope.regionSubnetPurposes = null;
      }
      $scope.regionSubnetPurposes = _($scope.subnets)
        .filter({account: $scope.command.credentials, region: $scope.command.region, target: 'ec2'})
        .pluck('purpose')
        .uniq()
        .map(function(purpose) { return { purpose: purpose, label: purpose };})
        .valueOf();
    }

    function configureSecurityGroupOptions() {
      if (true) { return; }

      var newRegionalSecurityGroups = _($scope.securityGroups[$scope.command.credentials].aws[$scope.command.region])
        .filter({vpcId: $scope.command.vpcId || null})
        .valueOf();
      if ($scope.regionalSecurityGroups && $scope.command.securityGroups) {
        var previousCount = $scope.command.securityGroups.length;
        // not initializing - we are actually changing groups
        var matchedGroupNames = $scope.command.securityGroups.map(function(groupId) {
          return _($scope.regionalSecurityGroups).find({id: groupId}).name;
        }).map(function(groupName) {
          return _(newRegionalSecurityGroups).find({name: groupName});
        }).filter(function(group) {
          return group;
        }).map(function(group) {
          return group.id;
        });
        $scope.command.securityGroups = matchedGroupNames;
        if (matchedGroupNames.length !== previousCount) {
          modalWizardService.getWizard().markDirty('security-groups');
        }

      }

      $scope.regionalSecurityGroups = newRegionalSecurityGroups;
    }

    function configureLoadBalancerOptions() {
      var newLoadBalancers = _($scope.loadBalancers)
        .filter({account: $scope.command.credentials})
        .pluck('regions')
        .flatten(true)
        .filter({name: $scope.command.region})
        .pluck('loadBalancers')
        .flatten(true)
        .pluck('name')
        .unique()
        .valueOf();

      if ($scope.regionalLoadBalancers && $scope.command.loadBalancers) {
        var previousCount = $scope.command.loadBalancers.length;
        $scope.command.loadBalancers = _.intersection(newLoadBalancers, $scope.command.loadBalancers);
        if ($scope.command.loadBalancers.length !== previousCount) {
          modalWizardService.getWizard().markDirty('load-balancers');
        }
      }

      $scope.regionalLoadBalancers = newLoadBalancers;
    }

    function configureImages() {
      if ($scope.command.credentials != $scope.lastImageAccount) {
        imageService.findImages($scope.command.selectedProvider, application.name, serverGroupCommand.region, $scope.command.credentials).then(function(images) {
          $scope.gceImages = images;

          if ($scope.gceImages.indexOf($scope.command.image) === -1) {
            $scope.command.image = null;
          }
        });

        $scope.lastImageAccount = $scope.command.credentials;
      }
    }

    function configureInstanceTypes() {
      if ($scope.command.region) {
        $scope.regionalInstanceTypes = instanceTypeService.getAvailableTypesForRegions($scope.command.selectedProvider, $scope.instanceTypesByRegion, [$scope.command.region]);
        if ($scope.command.instanceType && result.indexOf($scope.command.instanceType) === -1) {
          $scope.regionalInstanceTypes.push($scope.command.instanceType);
        }
      }
    }

    function initializeCommand() {
      if (serverGroupCommand.viewState.imageId && $scope.gceImages.indexOf(serverGroupCommand.viewState.imageId) > -1) {
        $scope.command.image = serverGroupCommand.viewState.imageId;
      }
    }

    function transformInstanceMetadata() {
      var transformedInstanceMetadata = {};

      // The instanceMetadata is stored using 'key' and 'value' attributes to enable the Add/Remove behavior in the wizard.
      $scope.command.instanceMetadata.forEach(function(metadataPair) {
        transformedInstanceMetadata[metadataPair['key']] = metadataPair['value'];
      });

      // We use this list of load balancer names when 'Enabling' a server group.
      if ($scope.command.loadBalancers.length > 0) {
        transformedInstanceMetadata['load-balancer-names'] = $scope.command.loadBalancers.toString();
      }

      $scope.command.instanceMetadata = transformedInstanceMetadata;
    }

    // TODO(duftler): Update this to reflect current fields defined on dialog.
    this.isValid = function () {
      return $scope.command && ($scope.command.amiName !== null) && ($scope.command.application !== null) &&
        ($scope.command.credentials !== null) && ($scope.command.instanceType !== null) &&
        ($scope.command.region !== null) && ($scope.command.availabilityZones !== null) &&
        ($scope.command.capacity.min !== null) && ($scope.command.capacity.max !== null) &&
        ($scope.command.capacity.desired !== null) &&
        modalWizardService.getWizard().isComplete();
    };

    this.showSubmitButton = function () {
      return modalWizardService.getWizard().allPagesVisited();
    };

    $scope.taskMonitor.onApplicationRefresh = function handleApplicationRefreshComplete() {
      $scope.taskMonitor.task.getCompletedKatoTask().then(function(katoTask) {
        if (katoTask.resultObjects && katoTask.resultObjects.length && katoTask.resultObjects[0].serverGroupNames) {
          var newStateParams = {
            serverGroup: katoTask.resultObjects[0].serverGroupNames[0].split(':')[1],
            accountId: $scope.command.credentials,
            region: $scope.command.region
          };
          if (!$state.includes('**.clusters.**')) {
            $state.go('^.^.^.clusters.serverGroup', newStateParams);
          } else {
            if ($state.includes('**.serverGroup')) {
              $state.go('^.^.serverGroup', newStateParams);
            } else {
              if ($state.includes('**.clusters.*')) {
                $state.go('^.serverGroup', newStateParams);
              } else {
                $state.go('.serverGroup', newStateParams);
              }
            }
          }
        }
      });
    };

    this.clone = function () {
      $scope.taskMonitor.submit(
        function() {
          transformInstanceMetadata();

          return orcaService.cloneServerGroup($scope.command, application.name);
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
