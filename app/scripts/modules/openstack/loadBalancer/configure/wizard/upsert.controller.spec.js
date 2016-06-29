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

    this.testData = {
      loadBalancerList: [
        {account: 'account1', region: 'region1', name: 'lb111'},
        {account: 'account1', region: 'region1', name: 'lb112'},
        {account: 'account1', region: 'region1', name: 'lb113'},
        {account: 'account2', region: 'region1', name: 'lb211'},
        {account: 'account2', region: 'region1', name: 'lb212'},
        {account: 'account2', region: 'region1', name: 'lb213'}
      ],
      accountList: [
        {name: 'account1'},
        {name: 'account2'},
        {name: 'account3'}
      ],
      regionList: ['region1', 'region2', 'region3'],
      subnet: 'subnet1'
    };

    this.loadBalancerDefaults = {
      provider: 'openstack',
      account: settings.providers.openstack ? settings.providers.openstack.defaults.account : null,
      stack: '',
      detail: '',
      subnetId: '',
      floatingIpId: '',
      protocol: 'HTTPS',
      externalPort: 443,
      internalPort: 443,
      method: 'ROUND_ROBIN',
      healthMonitor: {
        type: 'HTTPS',
        method: 'GET',
        url: '/healthCheck',
        expectedStatusCodes: [200],
        delay: 10,
        timeout: 200,
        maxRetries: 2
      }
    };

    this.$scope = $rootScope.$new();

    this.mockState = {
      go: jasmine.createSpy(),
      includes: jasmine.createSpy().and.callFake(function() { return testSuite.mockState.stateIncludesLoadBalancerDetails; })
    };
    this.mockModal = {
      dismiss: jasmine.createSpy(),
      close: jasmine.createSpy()
    };
    this.mockApplication = {
      name: 'app',
      loadBalancers: {
        refresh: jasmine.createSpy(),
        onNextRefresh: jasmine.createSpy().and.callFake(function(scope, callback) {
          testSuite.applicationRefreshCallback = callback;
        })
      }
    };

    function addDeferredMock(obj, method) {
      obj[method] = jasmine.createSpy().and.callFake(function() {
        var d = $q.defer();
        obj[method].deferred = d;
        return d.promise;
      });
      return obj;
    }

    this.mockLoadBalancerReader = addDeferredMock({}, 'listLoadBalancers');
    this.mockAccountService = addDeferredMock({}, 'listAccounts');
    addDeferredMock(this.mockAccountService, 'getRegionsForAccount');
    this.mockLoadBalancerWriter = addDeferredMock({}, 'upsertLoadBalancer');
    this.mockTaskMonitor = {
      submit: jasmine.createSpy()
    };
    this.mockTaskMonitorService = {
      buildTaskMonitor: jasmine.createSpy().and.callFake(function(arg) {
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
      expect(this.ctrl.onSubnetChanged).toBeDefined();
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
      expect(this.$scope.regions).toEqual([]);
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
      expect(this.$scope.loadBalancer.healthMonitor.expectedStatusCodes).toEqual([200,302]);
      this.ctrl.addStatusCode();
      expect(this.$scope.loadBalancer.healthMonitor.expectedStatusCodes).toEqual([200,302]);
      this.ctrl.removeStatusCode(102);
      expect(this.$scope.loadBalancer.healthMonitor.expectedStatusCodes).toEqual([200,302]);
      this.ctrl.removeStatusCode(200);
      expect(this.$scope.loadBalancer.healthMonitor.expectedStatusCodes).toEqual([302]);
    });

    describe('& account list returned', function() {
      beforeEach(function() {
        this.mockAccountService.listAccounts.deferred.resolve(this.testData.accountList);
        this.$scope.$digest();
      });

      it('sets the account to the first one in the list', function() {
        expect(this.$scope.loadBalancer.account).toEqual(this.testData.accountList[0].name);
      });

      it('requests the list of regions', function() {
        expect(this.mockAccountService.getRegionsForAccount).toHaveBeenCalledWith('account1');
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
            this.mockAccountService.getRegionsForAccount.deferred.resolve(this.testData.regionList);
            this.$scope.$digest();

            //simulate select-field enforcing selection of the region
            this.$scope.loadBalancer.region = this.testData.regionList[0];
            this.$scope.$digest();

            //simulate forced selection by select-field directive
            this.ctrl.regionUpdated();
          });

          it('- updates the subnet filter', function() {
            expect(this.$scope.subnetFilter).toEqual({type: 'openstack', account: this.$scope.loadBalancer.account, region: this.$scope.loadBalancer.region});
          });

          describe('& subnet updated', function() {
            beforeEach(function() {
              this.$scope.loadBalancer.subnet = this.testData.subnet;
              this.ctrl.onSubnetChanged();
            });

            it('loads the list of floating IPs', function() {
              //TODO (jcwest)... work in progress.... may not be needed
            });
          });

          describe('& account selection changed', function() {
            beforeEach(function() {
              this.$scope.loadBalancer.account = 'account2';
              this.ctrl.accountUpdated();
            });

            it('requests the list of regions', function() {
              expect(this.mockAccountService.getRegionsForAccount).toHaveBeenCalledWith('account2');
            });

            it('- updates the list of load balancer names', function() {
              expect(this.$scope.existingLoadBalancerNames).toEqual(
                _.map(_.filter(this.testData.loadBalancerList, {account: 'account2'}), function(lb) { return lb.name; })
              );
            });

            describe('& account selection changed again', function() {
              var firstDeferred;
              beforeEach(function() {
                firstDeferred = this.mockAccountService.getRegionsForAccount.deferred;
                this.$scope.loadBalancer.account = 'account1';
                this.ctrl.accountUpdated();
              });

              it('- ignores the response to the first query if it comes second', function() {
                this.mockAccountService.getRegionsForAccount.deferred.resolve(['second']);
                this.$scope.$digest();
                firstDeferred.resolve([]);
                this.$scope.$digest();
                expect(this.$scope.regions).toEqual([{label: 'second', value: 'second'}]);
              });
            });
          });

          describe('& region selection changed', function() {
            beforeEach(function() {
              this.$scope.loadBalancer.account = 'account2';
              this.ctrl.regionUpdated();
            });

            it('- updates the subnet filter', function() {
              expect(this.$scope.subnetFilter).toEqual({type: 'openstack', account: this.$scope.loadBalancer.account, region: this.$scope.loadBalancer.region});
            });
          });

          describe('& subnet selection changed', function() {
            //TODO(jcwest): loads floating IPs
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
                    cloudProvider: 'openstack'
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
                  cloudProvider: 'openstack'
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
      expect(this.ctrl.onSubnetChanged).toBeDefined();
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
      expect(this.$scope.regions).toEqual([]);
      expect(this.$scope.subnetFilter).toEqual({});
      expect(this.$scope.loadBalancer).toEqual(_.defaults(angular.copy(this.testData.loadBalancerList[3]),this.loadBalancerDefaults));
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
            cloudProvider: 'openstack'
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
