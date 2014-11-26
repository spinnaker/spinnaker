'use strict';


angular.module('deckApp.aws')
  .controller('awsCloneServerGroupCtrl', function($scope, $modalInstance, _, $q, $exceptionHandler, $state, settings,
                                               accountService, orcaService, mortService, oortService,
                                               instanceTypeService, modalWizardService, securityGroupService, taskMonitorService,
                                               imageService,
                                               serverGroupCommand, application, title) {
    $scope.title = title;
    $scope.healthCheckTypes = ['EC2', 'ELB'];
    $scope.terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];

    $scope.applicationName = application.name;

    $scope.state = {
      loaded: false,
      imagesLoaded: false,
    };

    $scope.taskMonitor = taskMonitorService.buildTaskMonitor({
      application: application,
      title: 'Creating your server group',
      forceRefreshMessage: 'Getting your new server group from Amazon...',
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

    var loadBalancerLoader = oortService.listLoadBalancers().then(function(loadBalancers) {
      $scope.loadBalancers = loadBalancers;
    });

    var subnetLoader = mortService.listSubnets().then(function(subnets) {
      $scope.subnets = subnets;
    });

    var preferredZonesLoader = accountService.getPreferredZonesByAccount().then(function(preferredZones) {
      $scope.preferredZones = preferredZones;
    });

    var keyPairLoader = mortService.listKeyPairs().then(function(keyPairs) {
      $scope.keyPairs = keyPairs;
    });

    $scope.command = serverGroupCommand;

    var imageLoader = imageService.findImages(application.name, serverGroupCommand.region, serverGroupCommand.credentials).then(function(images) {
      $scope.packageImages = images;
      if (images.length === 0) {
        if (serverGroupCommand.viewState.mode === 'clone') {
          imageService.getAmi(serverGroupCommand.viewState.imageId, serverGroupCommand.region, serverGroupCommand.credentials).then(function (namedImage) {
            if (namedImage) {
              var packageRegex = /(\w+)-?\w+/;
              $scope.command.amiName = namedImage.imageName;
              var match = packageRegex.exec(namedImage.imageName);
              $scope.packageBase = match[1];
              imageService.findImages($scope.packageBase, serverGroupCommand.region, serverGroupCommand.credentials).then(function(searchResults) {
                $scope.packageImages = searchResults;
                $scope.state.imagesLoaded = true;
                configureImages();
              });
            } else {
              $scope.command.viewState.useAllImageSelection = true;
              $scope.state.imagesLoaded = true;
            }
          });
        } else {
          $scope.state.imagesLoaded = true;
          $scope.command.viewState.useAllImageSelection = true;
        }
      } else {
        $scope.state.imagesLoaded = true;
      }
    });

    $q.all([accountLoader, securityGroupLoader, loadBalancerLoader, subnetLoader, imageLoader, preferredZonesLoader, keyPairLoader]).then(function() {
      $scope.state.loaded = true;
      initializeCommand();
      initializeWizardState();
      initializeSelectOptions();
      initializeWatches();
    });

    function initializeWizardState() {
      if (serverGroupCommand.viewState.mode === 'clone') {
        modalWizardService.getWizard().markComplete('location');
        modalWizardService.getWizard().markComplete('load-balancers');
        modalWizardService.getWizard().markComplete('security-groups');
        modalWizardService.getWizard().markComplete('instance-profile');
        modalWizardService.getWizard().markComplete('capacity');
        modalWizardService.getWizard().markComplete('advanced');
      }
    }

    function initializeWatches() {
      $scope.$watch('command.credentials', credentialsChanged);
      $scope.$watch('command.region', regionChanged);
      $scope.$watch('command.subnetType', subnetChanged);
      $scope.$watch('command.viewState.usePreferredZones', usePreferredZonesToggled);
    }

    function initializeSelectOptions() {
      credentialsChanged();
      regionChanged();
      configureSubnetPurposes();
      configureSecurityGroupOptions();
    }

    function credentialsChanged() {
      if ($scope.command.credentials) {
        $scope.regions = $scope.regionsKeyedByAccount[$scope.command.credentials].regions;
        $scope.command.keyPair = $scope.regionsKeyedByAccount[$scope.command.credentials].defaultKeyPair;
        if (!_($scope.regions).some({name: $scope.command.region})) {
          $scope.command.region = null;
        } else {
          regionChanged();
        }
      } else {
        $scope.command.region = null;
      }
    }

    function regionChanged() {
      var command = $scope.command;
      configureSubnetPurposes();
      var currentZoneCount = command.availabilityZones ? command.availabilityZones.length : 0;
      if (command.region) {
        if (!_($scope.regionSubnetPurposes).some({purpose: command.subnetType})) {
          command.subnetType = null;
        }
        subnetChanged();
        configureInstanceTypes();
        configureAvailabilityZones();
        configureImages();
        configureKeyPairs();
      } else {
        $scope.regionalAvailabilityZones = null;
      }

      usePreferredZonesToggled();
      if (!command.viewState.usePreferredZones) {
        command.availabilityZones = _.intersection(command.availabilityZones, $scope.regionalAvailabilityZones);
        var newZoneCount = command.availabilityZones ? command.availabilityZones.length : 0;
        if (currentZoneCount !== newZoneCount) {
          modalWizardService.getWizard().markDirty('capacity');
        }
      }
    }

    function usePreferredZonesToggled() {
      var command = $scope.command;
      if (command.viewState.usePreferredZones) {
        command.availabilityZones = $scope.preferredZones[command.credentials][command.region].sort();
      }
    }

    function subnetChanged() {
      if (!$scope.command.subnetType) {
        $scope.command.vpcId = null;
      } else {
        var subnet = _($scope.subnets)
          .find({purpose: $scope.command.subnetType, account: $scope.command.credentials, region: $scope.command.region});
        $scope.command.vpcId = subnet ? subnet.vpcId : null;
      }
      configureSecurityGroupOptions();
      configureLoadBalancerOptions();
    }

    function configureAvailabilityZones() {
      $scope.regionalAvailabilityZones = _.find($scope.regionsKeyedByAccount[$scope.command.credentials].regions,
        {name: $scope.command.region}).availabilityZones;
    }

    function configureSubnetPurposes() {
      if ($scope.command.region === null) {
        $scope.regionSubnetPurposes = null;
      }
      $scope.regionSubnetPurposes = _($scope.subnets)
        .filter({account: $scope.command.credentials, region: $scope.command.region})
        .reject({target: 'elb'})
        .reject({purpose: null})
        .pluck('purpose')
        .uniq()
        .map(function(purpose) { return { purpose: purpose, label: purpose };})
        .valueOf();
    }

    function configureSecurityGroupOptions() {
      var newRegionalSecurityGroups = _($scope.securityGroups[$scope.command.credentials].aws[$scope.command.region])
        .filter({vpcId: $scope.command.vpcId || null})
        .valueOf();
      if ($scope.regionalSecurityGroups && $scope.command.securityGroups) {
        var previousCount = $scope.command.securityGroups.length;
        // not initializing - we are actually changing groups
        var matchedGroupNames = $scope.command.securityGroups.map(function(groupId) {
          var securityGroup = _($scope.regionalSecurityGroups).find({id: groupId}) ||
            _($scope.regionalSecurityGroups).find({name: groupId});
          return securityGroup ? securityGroup.name : null;
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
        .pluck('accounts')
        .flatten(true)
        .filter({name: $scope.command.credentials})
        .pluck('regions')
        .flatten(true)
        .filter({name: $scope.command.region})
        .pluck('loadBalancers')
        .flatten(true)
        .filter({vpcId: $scope.command.vpcId})
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
      if ($scope.command.region) {
        $scope.regionalImages = $scope.packageImages.
          filter(function (image) {
            return image.amis && image.amis[$scope.command.region];
          }).
          map(function (image) {
            return { imageName: image.imageName, ami: image.amis ? image.amis[$scope.command.region][0] : null }
          });
        if ($scope.command.amiName && !$scope.regionalImages.some(function (image) {
          return image.imageName === $scope.command.amiName;
        })) {
          $scope.command.amiName = null;
        }
      } else {
        $scope.regionalImages = null;
        $scope.command.amiName = null;
      }
    }

    function configureInstanceTypes() {
      if ($scope.command.region) {
        instanceTypeService.getAvailableTypesForRegions($scope.command.selectedProvider, [$scope.command.region]).then(function (result) {
          $scope.regionalInstanceTypes = result;
          if ($scope.command.instanceType && result.indexOf($scope.command.instanceType) === -1) {
            $scope.regionalInstanceTypes.push($scope.command.instanceType);
          }
        });
      }
    }

    function configureKeyPairs() {
      $scope.regionalKeyPairs = _($scope.keyPairs)
        .filter({'account': $scope.command.credentials, 'region': $scope.command.region})
        .pluck('keyName')
        .valueOf();
    }

    function initializeCommand() {
      if (serverGroupCommand.viewState.imageId) {
        var foundImage = $scope.packageImages.filter(function(image) {
          return image.amis[serverGroupCommand.region] && image.amis[serverGroupCommand.region].indexOf(serverGroupCommand.viewState.imageId) !== -1;
        });
        if (foundImage.length) {
          serverGroupCommand.amiName = foundImage[0].imageName;
        }
      }
    }

    this.isValid = function () {
      return $scope.command &&
        ($scope.command.viewState.useAllImageSelection ? $scope.command.viewState.allImageSelection !== null : $scope.command.amiName !== null) &&
        ($scope.command.application !== null) &&
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
          var transitionTo = '^.^.^.clusters.serverGroup';
          if ($state.includes('**.clusters.serverGroup')) {  // clone via details, all view
            transitionTo = '^.serverGroup';
          }
          if ($state.includes('**.clusters.cluster.serverGroup')) { // clone or create with details open
            transitionTo = '^.^.serverGroup';
          }
          if ($state.includes('**.clusters')) { // create new, no details open
            transitionTo = '.serverGroup';
          }
          $state.go(transitionTo, newStateParams);
        }
      });
    };

    this.clone = function () {
      $scope.taskMonitor.submit(
        function() {
          return orcaService.cloneServerGroup($scope.command, application.name);
        }
      );
    };

    this.cancel = function () {
      $modalInstance.dismiss();
    };
  });
