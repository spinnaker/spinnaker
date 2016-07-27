'use strict';

describe('Controller: openstackSecurityGroupDetailsController', function() {

  //load the controller module
  beforeEach(
    window.module(require('./details.controller'))
  );

  // Initialize the controller and a mock scope
  var testSuite;
  beforeEach(window.inject(function ($controller, $rootScope, $q, settings) {
    testSuite = this;
    this.settings = settings;


    this.editSecurityGroup = {
        name: 'example-TestSecurityGroup1',
        region: 'TestRegion1',
        accountId: 'example',
        id: '0000-0000-0000-0000',
        vpcId: '0000-0000-0000-0000',
        provider: 'openstack',
      };

    this.$scope = $rootScope.$new();

    function addDeferredMock(obj, method) {
      obj[method] = jasmine.createSpy().and.callFake(function () {
        var d = $q.defer();
        obj[method].deferred = d;
        return d.promise;
      });
      return obj;
    }

    this.mockState = {
      go: jasmine.createSpy()
    };
    this.mockModal = {
      confirm: jasmine.createSpy(),
      open: jasmine.createSpy()
    };
    this.mockApplication = {
      isStandalone : true
    };

    this.mockSecurityGroupReader = addDeferredMock({}, 'getSecurityGroupDetails');
    this.mockSecurityGroupWriter = addDeferredMock({}, 'deleteSecurityGroup');

    this.mockcloudProviderRegistry = addDeferredMock({}, 'getValue');

    this.createController = function (resolvedSecurityGroup) {
      this.ctrl = $controller('openstackSecurityGroupDetailsController', {
        $scope: this.$scope,
        $uibModal: this.mockModal,
        $state: this.mockState,
        app: this.mockApplication,
        resolvedSecurityGroup: resolvedSecurityGroup ,

        securityGroupReader: this.mockSecurityGroupReader,
        securityGroupWriter: this.mockSecurityGroupWriter,

        cloudProviderRegistry: this.mockcloudProviderRegistry,


        confirmationModalService: this.mockState
      });
    };
  }));

  describe('initialized for create', function () {

    beforeEach(function () {
      this.createController(this.editSecurityGroup);
    });

    it('has the expected methods and properties', function () {
      expect(this.ctrl.editSecurityGroup).toBeDefined();
      expect(this.ctrl.deleteSecurityGroup).toBeDefined();
    });

    it('initializes the scope', function () {
      expect(this.$scope.state).toEqual({
        loading: true,
        standalone: true
      });
    });

    it('security group for edit', function () {
      expect(this.mockSecurityGroupReader.getSecurityGroupDetails).toHaveBeenCalledWith(this.mockApplication,
        this.editSecurityGroup.accountId, this.editSecurityGroup.provider, this.editSecurityGroup.region,
        this.editSecurityGroup.vpcId, this.editSecurityGroup.name);
    });

    describe('security group object should get set.', function () {
      beforeEach(function () {

        var securityGroup = this.editSecurityGroup;
        var details =
        {
          plain : function () {
            return securityGroup;
          }
        };
        this.mockSecurityGroupReader.getSecurityGroupDetails.deferred.resolve(details);
        this.$scope.$digest();
      });

      it('should set security group region', function () {
        expect(this.$scope.state.loading).toEqual(false);
      });
    });
  });
});
