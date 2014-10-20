'use strict';

describe('Controller: awsCloneServerGroup', function () {

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(function() {
    inject(function ($controller, $rootScope, accountService, orcaService, mortService, oortService,
                     searchService, instanceTypeService, modalWizardService, securityGroupService, taskMonitorService, $q) {

      this.$scope = $rootScope.$new();
      this.accountService = accountService;
      this.orcaService = orcaService;
      this.mortService = mortService;
      this.oortService = oortService;
      this.searchService = searchService;
      this.instanceTypeService = instanceTypeService;
      this.modalWizardService = modalWizardService;
      this.securityGroupService = securityGroupService;
      this.taskMonitorService = taskMonitorService;
      this.$q = $q;
    });

    this.modalInstance = {
      result: {
        then: angular.noop
      }
    };

    var spec = this;
    this.deferImmediately = function(result) {
      return function() { return spec.$q.when(result); };
    }
  });

  function initController(serverGroup) {
    inject(function ($controller) {
      this.ctrl = $controller('awsCloneServerGroupCtrl', {
        $scope: this.$scope,
        $modalInstance: this.modalInstance,
        accountService: this.accountService,
        orcaService: this.orcaService,
        mortService: this.mortService,
        oortService: this.oortService,
        searchService: this.searchService,
        instanceTypeService: this.instanceTypeService,
        modalWizardService: this.modalWizardService,
        securityGroupService: this.securityGroupService,
        taskMonitorService: this.taskMonitorService,
        serverGroup: serverGroup,
        application: {name: 'x'},
        title: 'n/a'
      });
    });
  }

  describe('preferred zone handling', function() {
    function setupMocks() {
      var resolve = this.deferImmediately;

      this.wizard = jasmine.createSpyObj('wizard', ['markDirty', 'markComplete']);
      spyOn(this.accountService, 'getPreferredZonesByAccount').andCallFake(resolve(AccountServiceFixture.preferredZonesByAccount));
      spyOn(this.accountService, 'getRegionsKeyedByAccount').andCallFake(resolve(AccountServiceFixture.regionsKeyedByAccount));
      spyOn(this.mortService, 'listSubnets').andCallFake(resolve([]));
      spyOn(this.mortService, 'listKeyPairs').andCallFake(resolve([]));
      spyOn(this.securityGroupService, 'getAllSecurityGroups').andCallFake(resolve(SecurityGroupServiceFixture.allSecurityGroups));
      spyOn(this.oortService, 'listLoadBalancers').andCallFake(resolve([]));
      spyOn(this.searchService, 'search').andCallFake(resolve({results: []}));
      spyOn(this.modalWizardService, 'getWizard').andReturn(this.wizard);

      spyOn(this.instanceTypeService, 'getAvailableTypesForRegions').andCallFake(resolve([]));
    }

    it('initializes to default values, setting usePreferredZone flag to true', function () {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      initController();

      $scope.$digest();

      expect($scope.command.usePreferredZones).toBe(true);
      expect($scope.command.availabilityZones).toEqual(['a', 'b', 'c']);
    });

    it('sets usePreferredZones flag based on initial value', function() {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      var serverGroup = {
        account: 'prod',
        region: 'us-west-1',
        asg: {
          availabilityZones: ['g','h','i'],
          vpczoneIdentifier: ''
        },
        launchConfig: {
          imageId: 'ami-123',
          instanceMonitoring: {
            enabled: true
          },
          securityGroups: []
        }
      };

      initController(serverGroup);

      $scope.$digest();

      expect($scope.command.usePreferredZones).toBe(true);
      expect($scope.command.availabilityZones).toEqual(['g', 'h', 'i']);

      serverGroup.asg.availabilityZones = ['g'];
      initController(serverGroup);

      $scope.$digest();

      expect($scope.command.usePreferredZones).toBe(false);
      expect($scope.command.availabilityZones).toEqual(['g']);

    });

    it('updates to default values when credentials changed', function() {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      initController();

      $scope.$digest();

      $scope.command.credentials = 'prod';
      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['d', 'e']);
    });

    it('updates to default values when region changed', function() {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      initController();

      $scope.$digest();

      $scope.command.region = 'us-west-1';
      $scope.$digest();

      expect($scope.command.usePreferredZones).toBe(true);
      expect($scope.command.availabilityZones).toEqual(['c', 'd']);
      expect(this.wizard.markDirty.calls.length).toBe(0);
    });

    it('clears availability zones when region changed and not using preferred values', function() {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      initController();

      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['a','b','c']);

      $scope.command.region = 'us-west-1';
      $scope.command.usePreferredZones = false;
      $scope.$digest();

      expect($scope.command.usePreferredZones).toBe(false);
      expect($scope.command.availabilityZones).toEqual(['b','c']);
      expect(this.wizard.markDirty.calls.length).toBe(1);
    });

    it('sets/clears availability zones to preferred zones when toggled on/off', function() {
      var $scope = this.$scope;
      setupMocks.bind(this).call();

      initController();

      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['a','b','c']);
      expect($scope.command.usePreferredZones).toBe(true);

      $scope.command.usePreferredZones = false;
      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['a','b','c']);
      expect($scope.command.usePreferredZones).toBe(false);

      $scope.command.availabilityZones = [];
      $scope.command.usePreferredZones = true;

      $scope.$digest();

      expect($scope.command.availabilityZones).toEqual(['a','b','c']);
      expect($scope.command.usePreferredZones).toBe(true);


    });
  });

});
