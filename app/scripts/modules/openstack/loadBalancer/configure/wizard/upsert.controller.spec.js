'use strict';

describe('Controller: openstackCreateLoadBalancerCtrl', function () {

  // load the controller's module
  beforeEach(
    window.module(
      require('./upsert.controller')
    )
  );

  // Initialize the controller and a mock scope
  var testSuite;
  beforeEach(window.inject(function ($controller, $rootScope, $q, settings) {
    testSuite = this;
    this.settings = settings;

    this.loadBalancerDefaults = {
      provider: 'openstack',
      account: settings.providers.openstack ? settings.providers.openstack.defaults.account : null,
      stack: '',
      detail: '',
      subnetId: '',
      networkId: '',
      algorithm: 'ROUND_ROBIN',
      healthMonitor: {
        type: 'HTTPS',
        httpMethod: 'GET',
        url: '/healthCheck',
        expectedCodes: [200],
        delay: 10,
        timeout: 1,
        maxRetries: 2
      },
      securityGroups: [],
      listeners: [
        {
          internalProtocol: 'HTTPS',
          internalPort: 443,
          externalProtocol: 'HTTPS',
          externalPort: 443
        }
      ]
    };

    this.testData = {
      loadBalancerList: [
        {account: 'account1', region: 'region1', name: 'lb111', healthChecks: [this.loadBalancerDefaults.healthMonitor]},
        {account: 'account1', region: 'region1', name: 'lb112', healthChecks: [this.loadBalancerDefaults.healthMonitor]},
        {account: 'account1', region: 'region1', name: 'lb113', healthChecks: [this.loadBalancerDefaults.healthMonitor]},
        {account: 'account2', region: 'region1', name: 'lb211', healthChecks: [this.loadBalancerDefaults.healthMonitor]},
        {account: 'account2', region: 'region1', name: 'lb212', healthChecks: [this.loadBalancerDefaults.healthMonitor]},
        {account: 'account2', region: 'region1', name: 'lb213', healthChecks: [this.loadBalancerDefaults.healthMonitor]}
      ],
      accountList: [
        {name: 'account1'},
        {name: 'account2'},
        {name: 'account3'}
      ],
      regionList: ['region1', 'region2', 'region3'],
      subnet: 'subnet1'
    };


    this.$scope = $rootScope.$new();

    this.mockState = {
      go: jasmine.createSpy('state.go'),
      includes: jasmine.createSpy('state.includes').and.callFake(function() { return testSuite.mockState.stateIncludesLoadBalancerDetails; })
    };
    this.mockModal = {
      dismiss: jasmine.createSpy('modal.dismiss'),
      close: jasmine.createSpy('modal.close')
    };
    this.mockApplication = {
      name: 'app',
      loadBalancers: {
        refresh: jasmine.createSpy('application.loadBalancers.refresh'),
        onNextRefresh: jasmine.createSpy('application.loadBalancers.onNextRefresh').and.callFake(function(scope, callback) {
          testSuite.applicationRefreshCallback = callback;
        })
      }
    };

    function addDeferredMock(obj, method) {
      obj[method] = jasmine.createSpy(method).and.callFake(function() {
        var d = $q.defer();
        obj[method].deferred = d;
        return d.promise;
      });
      return obj;
    }

    this.mockLoadBalancerReader = addDeferredMock({}, 'listLoadBalancers');
    this.mockAccountService = addDeferredMock({}, 'listAccounts');
    this.mockLoadBalancerWriter = addDeferredMock({}, 'upsertLoadBalancer');
    this.mockTaskMonitor = {
      submit: jasmine.createSpy('taskMonitor.submit')
    };
    this.mockTaskMonitorService = {
      buildTaskMonitor: jasmine.createSpy('taskMonitorService.buildTaskMonitor').and.callFake(function(arg) {
        testSuite.taskCompletionCallback = arg.onTaskComplete;
        return testSuite.mockTaskMonitor;
      })
    };

    this.createController = function(loadBalancer) {
      this.ctrl = $controller('openstackUpsertLoadBalancerController', {
        $scope: this.$scope,
        $uibModalInstance: this.mockModal,
        $state: this.mockState,
        application: this.mockApplication,
        loadBalancer: loadBalancer,
        isNew: !loadBalancer,
        loadBalancerReader: this.mockLoadBalancerReader,
        accountService: this.mockAccountService,
        loadBalancerWriter: this.mockLoadBalancerWriter,
        taskMonitorService: this.mockTaskMonitorService
      });
    };
  }));

  describe('initialized for create', function() {
    beforeEach(function() {
      this.createController(null);
    });

    it('has the expected methods and properties', function() {
      expect(this.ctrl.updateName).toBeDefined();
      expect(this.ctrl.accountUpdated).toBeDefined();
      expect(this.ctrl.addStatusCode).toBeDefined();
      expect(this.ctrl.removeStatusCode).toBeDefined();
      expect(this.ctrl.prependForwardSlash).toBeDefined();
      expect(this.ctrl.submit).toBeDefined();
      expect(this.ctrl.cancel).toBeDefined();
    });

    it('initializes the scope', function() {
      expect(this.$scope.state).toEqual({
        accountsLoaded: false,
        loadBalancerNamesLoaded: false,
        submitting: false
      });
      expect(this.$scope.isNew).toBeTruthy();
      expect(this.$scope.subnetFilter).toEqual({});
      expect(this.$scope.loadBalancer).toEqual(this.loadBalancerDefaults);
    });

    it('builds the task monitor', function() {
      expect(this.mockTaskMonitorService.buildTaskMonitor).toHaveBeenCalled();
    });

    it('requests the list of existing load balancers', function() {
      expect(this.mockLoadBalancerReader.listLoadBalancers).toHaveBeenCalled();
    });

    it('requests the list of accounts', function() {
      expect(this.mockAccountService.listAccounts).toHaveBeenCalledWith('openstack');
    });

    it('prepends forward slashes (for health check path)', function() {
      expect(this.ctrl.prependForwardSlash('test/one')).toEqual('/test/one');
      expect(this.ctrl.prependForwardSlash('/test/two')).toEqual('/test/two');
    });

    it('can add and remove health check status codes', function() {
      this.ctrl.newStatusCode = 302;
      this.ctrl.addStatusCode();
      expect(this.$scope.loadBalancer.healthMonitor.expectedCodes).toEqual([200,302]);
      this.ctrl.addStatusCode();
      expect(this.$scope.loadBalancer.healthMonitor.expectedCodes).toEqual([200,302]);
      this.ctrl.removeStatusCode(102);
      expect(this.$scope.loadBalancer.healthMonitor.expectedCodes).toEqual([200,302]);
      this.ctrl.removeStatusCode(200);
      expect(this.$scope.loadBalancer.healthMonitor.expectedCodes).toEqual([302]);
    });

    describe('& account list returned', function() {
      beforeEach(function() {
        this.mockAccountService.listAccounts.deferred.resolve(this.testData.accountList);
        this.$scope.$digest();
      });

      it('sets the account to the first one in the list', function() {
        expect(this.$scope.loadBalancer.account).toEqual(this.testData.accountList[0].name);
      });

      describe('& load balancer list returned', function() {
        beforeEach(function() {
          this.mockLoadBalancerReader.listLoadBalancers.deferred.resolve(this.testData.loadBalancerList);
          this.$scope.$digest();
        });

        it('- updates the list of load balancer names', function() {
          expect(this.$scope.state.loadBalancerNamesLoaded).toBeTruthy();
          expect(this.$scope.existingLoadBalancerNames).toEqual(
            _.map(_.filter(this.testData.loadBalancerList, {account: 'account1'}), function(lb) { return lb.name; })
          );
        });

        describe('& region list returned', function() {
          beforeEach(function() {
            //simulate forced selection by select-field directive
            this.ctrl.onRegionChanged(this.testData.regionList[0]);
            this.$scope.$digest();
          });

          it('- updates the subnet filter', function() {
            expect(this.$scope.subnetFilter).toEqual({type: 'openstack', account: this.$scope.loadBalancer.account, region: this.$scope.loadBalancer.region});
          });

          describe('& account selection changed', function() {
            beforeEach(function() {
              this.$scope.loadBalancer.account = 'account2';
              this.ctrl.accountUpdated();
            });

            it('- updates the list of load balancer names', function() {
              expect(this.$scope.existingLoadBalancerNames).toEqual(
                _.map(_.filter(this.testData.loadBalancerList, {account: 'account2'}), function(lb) { return lb.name; })
              );
            });
          });

          describe('& region selection changed', function() {
            beforeEach(function() {
              this.$scope.loadBalancer.account = 'account2';
              this.ctrl.onRegionChanged(this.testData.regionList[1]);
              this.$scope.$digest();
            });

            it('- updates the subnet filter', function() {
              expect(this.$scope.subnetFilter).toEqual({type: 'openstack', account: this.$scope.loadBalancer.account, region: this.$scope.loadBalancer.region});
            });
          });

          describe('& submit() called', function() {
            beforeEach(function() {
              this.ctrl.submit();
            });

            it('- calls mockTaskMonitor.submit()', function() {
              expect(this.mockTaskMonitor.submit).toHaveBeenCalled();
            });

            describe('& task monitor invokes callback', function() {
              beforeEach(function() {
                this.mockTaskMonitor.submit.calls.mostRecent().args[0]();
              });

              it('- calls upsertLoadBalancer()', function() {
                expect(this.mockLoadBalancerWriter.upsertLoadBalancer).toHaveBeenCalledWith(
                  this.$scope.loadBalancer, this.mockApplication, 'Create', {
                    cloudProvider: 'openstack',
                    account: 'account1',
                    accountId: undefined,
                    securityGroups: []
                  });
              });

              describe('& task completes', function() {
                beforeEach(function() {
                  this.taskCompletionCallback();
                });

                it('- refreshes the load balancers', function() {
                  expect(this.mockApplication.loadBalancers.refresh).toHaveBeenCalled();
                });

                describe('& load balancers are refreshed', function() {
                  beforeEach(function() {
                    this.applicationRefreshCallback();
                  });

                  it('- transitions to the next screen', function() {
                    expect(this.mockState.go).toHaveBeenCalledWith('.loadBalancerDetails', {
                      provider: 'openstack',
                      name: this.$scope.loadBalancer.name,
                      accountId: this.$scope.loadBalancer.account,
                      region: this.$scope.loadBalancer.region,
                    });
                  });
                });

                describe('& user closes the dialog', function() {
                  beforeEach(function() {
                    this.$scope.$$destroyed = true;
                  });

                  afterEach(function() {
                    this.$scope.$$destroyed = false;
                  });

                  describe('& load balancers are refreshed', function() {
                    beforeEach(function() {
                      this.applicationRefreshCallback();
                    });

                    it('- does nothing', function() {
                      expect(this.mockState.go).not.toHaveBeenCalled();
                    });
                  });
                });
              });
            });

            it('calls upsertLoadBalancer()', function() {
              expect(this.mockTaskMonitor.submit).toHaveBeenCalled();

              this.mockTaskMonitor.submit.calls.mostRecent().args[0]();
              expect(this.mockLoadBalancerWriter.upsertLoadBalancer).toHaveBeenCalledWith(
                this.$scope.loadBalancer, this.mockApplication, 'Create', {
                  cloudProvider: 'openstack',
                  account: 'account1',
                  accountId: undefined,
                  securityGroups: []
                });

              this.taskCompletionCallback();
              expect(this.mockApplication.loadBalancers.refresh).toHaveBeenCalled();

              this.applicationRefreshCallback();
              expect(this.mockModal.close).toHaveBeenCalled();
              expect(this.mockState.go).toHaveBeenCalledWith('.loadBalancerDetails', {
                provider: 'openstack',
                name: this.$scope.loadBalancer.name,
                accountId: this.$scope.loadBalancer.account,
                region: this.$scope.loadBalancer.region,
              });
            });
          });

          describe('cancel() called', function() {
            beforeEach(function() {
              this.ctrl.cancel();
            });

            it('closes the dialog', function() {
              expect(this.mockModal.dismiss).toHaveBeenCalled();
            });
          });
        });

      });
    });
  });

  describe('initialized for edit', function() {
    beforeEach(function() {
      this.settings.providers.openstack.defaults.account = 'account1';
      this.createController(angular.copy(this.testData.loadBalancerList[3]));
    });

    it('has the expected methods and properties', function() {
      expect(this.ctrl.updateName).toBeDefined();
      expect(this.ctrl.accountUpdated).toBeDefined();
      expect(this.ctrl.addStatusCode).toBeDefined();
      expect(this.ctrl.removeStatusCode).toBeDefined();
      expect(this.ctrl.prependForwardSlash).toBeDefined();
      expect(this.ctrl.submit).toBeDefined();
      expect(this.ctrl.cancel).toBeDefined();
    });

    it('initializes the scope', function() {
      expect(this.$scope.state).toEqual({
        accountsLoaded: false,
        loadBalancerNamesLoaded: false,
        submitting: false
      });
      expect(this.$scope.isNew).toBeFalsy();
      expect(this.$scope.subnetFilter).toEqual({});
      expect(this.$scope.loadBalancer).toEqual(_.defaults(angular.copy(this.testData.loadBalancerList[3]), this.loadBalancerDefaults));
    });

    describe('& account list returned', function() {
      beforeEach(function() {
        this.mockAccountService.listAccounts.deferred.resolve(this.testData.accountList);
        this.$scope.$digest();
      });

      it('does not change the account', function() {
        expect(this.$scope.loadBalancer.account).toEqual('account2');
      });
    });

    describe('submit() called', function() {
      beforeEach(function() {
        this.mockState.stateIncludesLoadBalancerDetails = true;
        this.ctrl.submit();
      });

      it('calls upsertLoadBalancer()', function() {
        expect(this.mockTaskMonitor.submit).toHaveBeenCalled();

        this.mockTaskMonitor.submit.calls.mostRecent().args[0]();
        expect(this.mockLoadBalancerWriter.upsertLoadBalancer).toHaveBeenCalledWith(
          this.$scope.loadBalancer, this.mockApplication, 'Update', {
            cloudProvider: 'openstack',
            account: 'account2',
            accountId: undefined,
            securityGroups: []
          });

        this.taskCompletionCallback();
        expect(this.mockApplication.loadBalancers.refresh).toHaveBeenCalled();

        this.applicationRefreshCallback();
        expect(this.mockModal.close).toHaveBeenCalled();
        expect(this.mockState.go).toHaveBeenCalledWith('^.loadBalancerDetails', {
          provider: 'openstack',
          name: this.$scope.loadBalancer.name,
          accountId: this.$scope.loadBalancer.account,
          region: this.$scope.loadBalancer.region,
        });
      });
    });

  });
});
