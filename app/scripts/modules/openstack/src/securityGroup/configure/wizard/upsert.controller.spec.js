import { AccountService, APPLICATION_MODEL_BUILDER, SecurityGroupWriter } from '@spinnaker/core';

import { OpenStackProviderSettings } from '../../../openstack.settings';

describe('Controller: openstackCreateSecurityGroupCtrl', function() {
  // load the controller's module
  beforeEach(window.module(require('./upsert.controller').name, APPLICATION_MODEL_BUILDER));

  afterEach(OpenStackProviderSettings.resetToOriginal);

  // Initialize the controller and a mock scope
  var testSuite;
  beforeEach(
    window.inject(function($controller, $rootScope, $q, applicationModelBuilder) {
      testSuite = this;
      OpenStackProviderSettings.defaults.account = 'account1';

      this.testData = {
        securityGroupList: [
          { account: 'account1', region: 'region1', name: 'sc111' },
          { account: 'account1', region: 'region1', name: 'sc112' },
          { account: 'account1', region: 'region1', name: 'sc113' },
          { account: 'account2', region: 'region1', name: 'sc211' },
          { account: 'account2', region: 'region1', name: 'sc212' },
          { account: 'account2', region: 'region1', name: 'sc213' },
        ],
        accountList: [{ name: 'account1' }, { name: 'account2' }, { name: 'account3' }],
        regionList: ['region1', 'region2', 'region3'],
      };

      this.testEditData = {
        account: undefined,
        region: 'region1',
        name: 'sc111',
        edit: true,
        rules: [],
        accountName: undefined,
        description: undefined,
        application: 'sc111',
        detail: '',
        stack: '',
      };

      this.securityGroupDefaults = {
        provider: 'openstack',
        region: '',
        application: 'app',
        stack: '',
        description: '',
        detail: '',
        account: 'account1',
        rules: [],
      };

      this.$scope = $rootScope.$new();

      this.mockState = {
        go: jasmine.createSpy(),
        includes: jasmine.createSpy().and.callFake(function() {
          return testSuite.mockState.stateIncludesSecurityGroupDetails;
        }),
      };
      this.mockModal = {
        dismiss: jasmine.createSpy(),
        close: jasmine.createSpy(),
        result: $q.when(null),
      };
      let application = applicationModelBuilder.createApplicationForTests('app', {
        key: 'securityGroups',
        onLoad: angular.noop,
        loader: angular.noop,
      });
      application.securityGroups.refresh = jasmine.createSpy();
      application.securityGroups.onNextRefresh = jasmine.createSpy().and.callFake(function(scope, callback) {
        testSuite.applicationRefreshCallback = callback;
      });
      this.mockApplication = application;

      function addDeferredMock(obj, method) {
        obj[method] = jasmine.createSpy().and.callFake(function() {
          var d = $q.defer();
          obj[method].deferred = d;
          return d.promise;
        });
        return obj;
      }

      spyOn(AccountService, 'listAccounts').and.returnValue($q.when(this.testData.accountList));
      spyOn(SecurityGroupWriter, 'upsertSecurityGroup');
      this.mockSecurityGroupReader = addDeferredMock({}, 'loadSecurityGroups');
      this.mockTaskMonitor = {
        submit: jasmine.createSpy(),
      };

      this.createController = function(securityGroup) {
        this.ctrl = $controller('openstackUpsertSecurityGroupController', {
          $scope: this.$scope,
          $uibModalInstance: this.mockModal,
          $state: this.mockState,
          application: this.mockApplication,
          securityGroup: securityGroup,
          securityGroupReader: this.mockSecurityGroupReader,
        });
      };
    }),
  );

  describe('initialized for create', function() {
    beforeEach(function() {
      var securityGroup = { edit: false };

      this.createController(securityGroup);
    });

    it('has the expected methods and properties', function() {
      expect(this.ctrl.updateName).toBeDefined();
      expect(this.ctrl.accountUpdated).toBeDefined();
      expect(this.ctrl.getName).toBeDefined();
      expect(this.ctrl.submit).toBeDefined();
      expect(this.ctrl.cancel).toBeDefined();
    });

    it('initializes the scope', function() {
      expect(this.$scope.state).toEqual({
        accountsLoaded: true,
        securityGroupNamesLoaded: false,
        submitting: false,
      });
      expect(this.$scope.isNew).toEqual(true);
      expect(this.$scope.securityGroup).toEqual(this.securityGroupDefaults);
    });

    it('builds the task monitor', function() {
      expect(this.$scope.taskMonitor).not.toBeUndefined();
    });

    it('requests the list of accounts', function() {
      expect(AccountService.listAccounts).toHaveBeenCalledWith('openstack');
    });

    describe('& account list returned', function() {
      beforeEach(function() {
        this.$scope.$digest();
      });

      it('sets the account to the first one in the list', function() {
        expect(this.$scope.securityGroup.account).toEqual(this.testData.accountList[0].name);
      });

      describe('& securityGroup list returned', function() {
        beforeEach(function() {
          this.mockSecurityGroupReader.loadSecurityGroups.deferred.resolve(this.testData.loadSecurityGroups);
          this.$scope.$digest();
        });

        it('- updates the list of firewall names', function() {
          expect(this.$scope.state.securityGroupNamesLoaded).toBeTruthy();
          expect(this.$scope.existingSecurityGroupNames).toEqual(
            _.map(_.filter(this.testData.loadSecurityGroups, { account: 'account1' }), function(lb) {
              return lb.name;
            }),
          );
        });

        describe('& account selection changed', function() {
          beforeEach(function() {
            spyOn(this.$scope.taskMonitor, 'submit');
            this.$scope.securityGroup.account = 'account2';
            this.ctrl.accountUpdated();
          });

          describe('& submit() called', function() {
            beforeEach(function() {
              this.ctrl.submit();
            });

            it('- calls mockTaskMonitor.submit()', function() {
              expect(this.$scope.taskMonitor.submit).toHaveBeenCalled();
            });

            describe('& task monitor invokes callback', function() {
              beforeEach(function() {
                this.$scope.taskMonitor.submit.calls.mostRecent().args[0]();
              });

              it('- calls upsertSecurityGroup()', function() {
                expect(SecurityGroupWriter.upsertSecurityGroup).toHaveBeenCalledWith(
                  this.$scope.securityGroup,
                  this.mockApplication,
                  'Create',
                  {
                    cloudProvider: 'openstack',
                  },
                );
              });

              describe('& task completes', function() {
                beforeEach(function() {
                  this.$scope.taskMonitor.onTaskComplete();
                });

                it('- refreshes the firewalls', function() {
                  expect(this.mockApplication.securityGroups.refresh).toHaveBeenCalled();
                });

                describe('& user closes the dialog', function() {
                  beforeEach(function() {
                    this.$scope.$$destroyed = true;
                  });

                  afterEach(function() {
                    this.$scope.$$destroyed = false;
                  });

                  describe('& firewalls are refreshed', function() {
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
      this.createController(angular.copy(this.testEditData));
    });

    it('has the expected methods and properties', function() {
      expect(this.ctrl.updateName).toBeDefined();
      expect(this.ctrl.getName).toBeDefined();
      expect(this.ctrl.onRegionChanged).toBeDefined();
      expect(this.ctrl.submit).toBeDefined();
      expect(this.ctrl.cancel).toBeDefined();
    });

    it('initializes the scope', function() {
      expect(this.$scope.state).toEqual({
        accountsLoaded: true,
        securityGroupNamesLoaded: false,
        submitting: false,
      });
      expect(this.$scope.isNew).toBeFalsy();
      expect(this.$scope.securityGroup).toEqual(_.defaults(angular.copy(this.testEditData)));
    });

    describe('submit() called', function() {
      beforeEach(function() {
        spyOn(this.$scope.taskMonitor, 'submit');
        this.mockState.stateIncludesSecurityGroupDetails = true;
        this.ctrl.submit();
      });

      it('calls upsertSecurityGroup()', function() {
        expect(this.$scope.taskMonitor.submit).toHaveBeenCalled();

        this.$scope.taskMonitor.onTaskComplete();
        expect(this.mockApplication.securityGroups.refresh).toHaveBeenCalled();

        this.applicationRefreshCallback();
        expect(this.mockModal.close).toHaveBeenCalled();
        expect(this.mockState.go).toHaveBeenCalledWith('^.firewallDetails', {
          name: this.$scope.securityGroup.name,
          accountId: this.$scope.securityGroup.account,
          namespace: undefined,
          provider: 'openstack',
        });
      });
    });
  });
});
