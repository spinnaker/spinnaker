'use strict';

/*
 This is more of an integration test between ServerGroupConfigurationService and awsCloneServerGroupCtrl.
 ServerGroupConfigurationService is not mocked out to verify behavior that existed before it was refactored into
 existence.
 */
describe('Controller: awsCloneServerGroup', function () {

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(function() {
    inject(function ($controller, $rootScope, accountService, orcaService, mortService, oortService, imageService, settings,
                     searchService, instanceTypeService, modalWizardService, securityGroupService, taskMonitorService, serverGroupService, $q) {

      this.$scope = $rootScope.$new();
      this.accountService = accountService;
      this.orcaService = orcaService;
      this.mortService = mortService;
      this.oortService = oortService;
      this.imageService = imageService;
      this.searchService = searchService;
      this.instanceTypeService = instanceTypeService;
      this.modalWizardService = modalWizardService;
      this.securityGroupService = securityGroupService;
      this.serverGroupService = serverGroupService;
      this.taskMonitorService = taskMonitorService;
      this.settings = settings;
      this.$q = $q;
    });

    this.modalInstance = {
      result: {
        then: angular.noop
      }
    };

    var spec = this;

    this.resolve = function(result) {
      return function() { return spec.$q.when(result); };
    };
    this.reject = function(result) {
      return function() { return spec.$q.reject(result); };
    };

    this.buildBaseClone = function() {
      return {
        credentials: 'prod',
        region: 'us-west-1',
        availabilityZones: ['g','h','i'],
        instanceMonitoring: true,
        securityGroups: [],
        selectedProvider: 'aws',
        source: {
          asgName: 'testasg-v002'
        },
        viewState: {
          mode: 'clone',
          imageId: 'ami-123',
          usePreferredZones: true,
        }
      };
    };

    this.buildBaseNew = function() {
      return {
        credentials: 'test',
        region: 'us-east-1',
        availabilityZones: AccountServiceFixture.regionsKeyedByAccount['test'].regions[0].availabilityZones,
        securityGroups: [],
        selectedProvider: 'aws',
        instanceMonitoring: true,
        viewState: {
          mode: 'create',
          usePreferredZones: true,
        }
      };
    };
  });

  describe('preferred zone handling', function() {
    function initController(serverGroupCommand) {
      inject(function ($controller) {
        this.ctrl = $controller('awsCloneServerGroupCtrl', {
          $scope: this.$scope,
          settings: this.settings,
          $modalInstance: this.modalInstance,
          accountService: this.accountService,
          orcaService: this.orcaService,
          mortService: this.mortService,
          oortService: this.oortService,
          imageService: this.imageService,
          searchService: this.searchService,
          instanceTypeService: this.instanceTypeService,
          modalWizardService: this.modalWizardService,
          securityGroupService: this.securityGroupService,
          serverGroupService: this.serverGroupService,
          taskMonitorService: this.taskMonitorService,
          serverGroupCommand: serverGroupCommand,
          application: {name: 'x'},
          title: 'n/a'
        });
      });
    }

    function setupMocks() {
      var resolve = this.resolve;

      this.wizard = jasmine.createSpyObj('wizard', ['markDirty', 'markComplete']);
      spyOn(this.accountService, 'getPreferredZonesByAccount').and.callFake(resolve(AccountServiceFixture.preferredZonesByAccount));
      spyOn(this.accountService, 'getRegionsKeyedByAccount').and.callFake(resolve(AccountServiceFixture.regionsKeyedByAccount));
      spyOn(this.mortService, 'listSubnets').and.callFake(resolve([]));
      spyOn(this.mortService, 'listKeyPairs').and.callFake(resolve([]));
      spyOn(this.securityGroupService, 'getAllSecurityGroups').and.callFake(resolve(SecurityGroupServiceFixture.allSecurityGroups));
      spyOn(this.oortService, 'listAWSLoadBalancers').and.callFake(resolve([]));
      spyOn(this.imageService, 'findImages').and.callFake(resolve([{amis: {'us-east-1': []}}]));

      spyOn(this.searchService, 'search').and.callFake(resolve({results: []}));
      spyOn(this.modalWizardService, 'getWizard').and.returnValue(this.wizard);
      spyOn(this.instanceTypeService, 'getAllTypesByRegion').and.callFake(resolve([]));
      spyOn(this.instanceTypeService, 'getAvailableTypesForRegions').and.callFake(resolve([]));
    }

    it('updates to default values when credentials changed', function() {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      initController(this.buildBaseNew());

      $scope.$digest();

      $scope.command.credentials = 'prod';
      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['d', 'e']);
    });

    it('updates to default values when region changed', function() {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      initController(this.buildBaseNew());

      $scope.$digest();

      $scope.command.region = 'us-west-1';
      $scope.$digest();

      expect($scope.command.viewState.usePreferredZones).toBe(true);
      expect($scope.command.availabilityZones).toEqual(['c', 'd']);
      expect(this.wizard.markDirty.calls.count()).toBe(0);
    });

    it('clears availability zones when region changed and not using preferred values', function() {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      initController(this.buildBaseNew());

      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['a','b','c']);

      $scope.command.region = 'us-west-1';
      $scope.command.viewState.usePreferredZones = false;
      $scope.$digest();

      expect($scope.command.viewState.usePreferredZones).toBe(false);
      expect($scope.command.availabilityZones).toEqual(['b','c']);
      expect(this.wizard.markDirty.calls.count()).toBe(1);
    });

    it('sets/clears availability zones to preferred zones when toggled on/off', function() {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      initController(this.buildBaseNew());

      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['a','b','c']);
      expect($scope.command.viewState.usePreferredZones).toBe(true);

      $scope.command.viewState.usePreferredZones = false;
      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['a','b','c']);
      expect($scope.command.viewState.usePreferredZones).toBe(false);

      $scope.command.availabilityZones = [];
      $scope.command.viewState.usePreferredZones = true;

      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['a','b','c']);
      expect($scope.command.viewState.usePreferredZones).toBe(true);
    });
  });

  describe('image loading', function() {
    function initController(serverGroupCommand) {
      inject(function ($controller) {
        this.ctrl = $controller('awsCloneServerGroupCtrl', {
          $scope: this.$scope,
          settings: this.settings,
          $modalInstance: this.modalInstance,
          accountService: this.accountService,
          orcaService: this.orcaService,
          instanceTypeService: this.instanceTypeService,
          modalWizardService: this.modalWizardService,
          taskMonitorService: this.taskMonitorService,
          serverGroupCommand: serverGroupCommand,
          application: {name: 'x'},
          title: 'n/a'
        });
      });
    }

    function setupMocks() {
      var resolve = this.resolve;

      this.wizard = jasmine.createSpyObj('wizard', ['markDirty', 'markComplete']);
      spyOn(this.accountService, 'getPreferredZonesByAccount').and.callFake(resolve(AccountServiceFixture.preferredZonesByAccount));
      spyOn(this.accountService, 'getRegionsKeyedByAccount').and.callFake(resolve(AccountServiceFixture.regionsKeyedByAccount));
      spyOn(this.mortService, 'listSubnets').and.callFake(resolve([]));
      spyOn(this.mortService, 'listKeyPairs').and.callFake(resolve([]));
      spyOn(this.securityGroupService, 'getAllSecurityGroups').and.callFake(resolve(SecurityGroupServiceFixture.allSecurityGroups));
      spyOn(this.oortService, 'listAWSLoadBalancers').and.callFake(resolve([]));

      spyOn(this.searchService, 'search').and.callFake(resolve({results: []}));
      spyOn(this.modalWizardService, 'getWizard').and.returnValue(this.wizard);
      spyOn(this.instanceTypeService, 'getAllTypesByRegion').and.callFake(resolve([]));
      spyOn(this.instanceTypeService, 'getAvailableTypesForRegions').and.callFake(resolve([]));
    }

    it('sets state flags useAllImageSelection when none found and no server group provided', function () {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      spyOn(this.imageService, 'findImages').and.callFake(this.resolve([]));

      initController(this.buildBaseNew());

      $scope.$digest();

      expect($scope.command.viewState.useAllImageSelection).toBe(true);
    });

    it('puts found images on scope when found', function () {
      var $scope = this.$scope,
        regionalImages = [
          {imageName: 'someImage', amis: {'us-east-1': ['ami-1']}}
        ];
      setupMocks.bind(this).call();

      spyOn(this.imageService, 'findImages').and.callFake(this.resolve(regionalImages));

      initController(this.buildBaseNew());

      $scope.$digest();

      expect($scope.command.viewState.useAllImageSelection).toBeFalsy();
      expect($scope.command.backingData.filtered.images.length).toBe(1);
      expect($scope.command.backingData.filtered.images[0]).toEqual({imageName: 'someImage', ami: 'ami-1'});
    });

    it('queries based on existing ami when cloning', function () {
      var context = this,
        $scope = this.$scope,
        amiBasedImage = {imageName: 'something-packagebase', amis: {'us-east-1': ['ami-1234']}},
        packageBasedImages = [amiBasedImage],
        serverGroup = this.buildBaseClone();
      setupMocks.bind(this).call();

      serverGroup.region = 'us-east-1';

      spyOn(this.imageService, 'findImages').and.callFake(function (provider, query) {
        if (query === 'something') {
          return context.resolve(packageBasedImages).call();
        } else {
          return context.resolve([]).call();
        }
      });

      spyOn(this.imageService, 'getAmi').and.callFake(this.resolve(amiBasedImage));

      initController(serverGroup);

      $scope.$digest();

      expect($scope.command.viewState.useAllImageSelection).toBeFalsy();
      expect(this.imageService.getAmi).toHaveBeenCalledWith('aws', serverGroup.viewState.imageId, serverGroup.region, serverGroup.credentials);
      expect(this.imageService.findImages).toHaveBeenCalledWith('aws', 'something');

      expect($scope.command.backingData.filtered.images.length).toBe(1);
      expect($scope.command.backingData.filtered.images[0]).toEqual({imageName: 'something-packagebase', ami: 'ami-1234'});
    });

    it('adds no regional images to the scope when the one provided does not match any results', function () {
      var $scope = this.$scope,
        serverGroup = this.buildBaseClone();
      setupMocks.bind(this).call();

      spyOn(this.imageService, 'findImages').and.callFake(this.resolve([]));
      spyOn(this.imageService, 'getAmi').and.callFake(this.reject(null));

      initController(serverGroup);

      $scope.$digest();

      expect($scope.command.viewState.useAllImageSelection).toBe(true);
      expect(this.imageService.getAmi).toHaveBeenCalledWith('aws', serverGroup.viewState.imageId, serverGroup.region, serverGroup.credentials);
      expect($scope.command.backingData.filtered.images).toEqual([]);
    });

    it('queries all images for ami when no regional images present', function () {
      var $scope = this.$scope;

      setupMocks.bind(this).call();

      spyOn(this.imageService, 'findImages').and.callFake(this.resolve([]));
      spyOn(this.imageService, 'getAmi').and.callFake(this.reject(null));

      initController(this.buildBaseNew());

      $scope.$digest();

      expect($scope.command.viewState.useAllImageSelection).toBe(true);
    });
  });

  describe('command submit', function() {
    function initController(serverGroup) {
      inject(function ($controller) {
        this.ctrl = $controller('awsCloneServerGroupCtrl', {
          $scope: this.$scope,
          settings: this.settings,
          $modalInstance: this.modalInstance,
          accountService: this.accountService,
          orcaService: this.orcaService,
          instanceTypeService: this.instanceTypeService,
          modalWizardService: this.modalWizardService,
          taskMonitorService: this.taskMonitorService,
          serverGroupCommand: serverGroup,
          application: {name: 'x'},
          title: 'n/a'
        });
      });
    }

    function setupMocks() {
      var resolve = this.resolve,
          spec = this;

      this.wizard = jasmine.createSpyObj('wizard', ['markDirty', 'markComplete']);
      spyOn(this.accountService, 'getPreferredZonesByAccount').and.callFake(resolve(AccountServiceFixture.preferredZonesByAccount));
      spyOn(this.accountService, 'getRegionsKeyedByAccount').and.callFake(resolve(AccountServiceFixture.regionsKeyedByAccount));
      spyOn(this.mortService, 'listSubnets').and.callFake(resolve([]));
      spyOn(this.mortService, 'listKeyPairs').and.callFake(resolve([]));
      spyOn(this.securityGroupService, 'getAllSecurityGroups').and.callFake(resolve(SecurityGroupServiceFixture.allSecurityGroups));
      spyOn(this.oortService, 'listAWSLoadBalancers').and.callFake(resolve([]));

      spyOn(this.searchService, 'search').and.callFake(resolve({results: []}));
      spyOn(this.modalWizardService, 'getWizard').and.returnValue(this.wizard);
      spyOn(this.instanceTypeService, 'getAllTypesByRegion').and.callFake(resolve([]));
      spyOn(this.instanceTypeService, 'getAvailableTypesForRegions').and.callFake(resolve([]));
      spyOn(this.imageService, 'findImages').and.callFake(this.resolve([]));
      spyOn(this.imageService, 'getAmi').and.callFake(this.reject(null));

      spyOn(this.orcaService, 'cloneServerGroup').and.callFake(function(command, applicationName, description) {
        spec.submitted = {
          command: command,
          applicationName: applicationName,
          description: description
        }
      });

    }

    it('updates vpcId when subnetType changes, ignoring subnets without a purpose', function() {
      var $scope = this.$scope,
        serverGroup = this.buildBaseClone();

      setupMocks.bind(this).call();

      initController(serverGroup);

      $scope.$digest();

      $scope.command.backingData.subnets = [
        { vpcId: 'vpc-1', account: 'prod', region: 'us-west-1' },
        { vpcId: 'vpc-2', account: 'prod', region: 'us-west-1', purpose: 'magic' }
      ];

      expect($scope.command.vpcId).toBe(null);

      $scope.command.subnetType = 'magic';
      $scope.$digest();
      expect($scope.command.vpcId).toBe('vpc-2');

      $scope.command.subnetType = '';
      $scope.$digest();
      expect($scope.command.vpcId).toBe(null);

    });
  });

});
