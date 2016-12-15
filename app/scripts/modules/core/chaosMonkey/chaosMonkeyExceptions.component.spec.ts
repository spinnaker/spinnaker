import {mock} from 'angular';

import {CHAOS_MONKEY_EXCEPTIONS_COMPONENT, ChaosMonkeyExceptionsController} from './chaosMonkeyExceptions.component';

describe('Controller: ChaosMonkeyExceptions', () => {

  let $componentController: ng.IComponentControllerService,
      accountService: any,
      $ctrl: ChaosMonkeyExceptionsController,
      $scope: ng.IScope,
      $q: ng.IQService;

  let initializeController = (data: any) => {
    $ctrl = <ChaosMonkeyExceptionsController> $componentController(
      'chaosMonkeyExceptions',
      { $scope: null, accountService: accountService, $q: $q },
      data
    );
  };

  beforeEach(mock.module(CHAOS_MONKEY_EXCEPTIONS_COMPONENT));

  beforeEach(mock.inject((
    _$componentController_: ng.IComponentControllerService,
    _$q_: ng.IQService,
    $rootScope: ng.IRootScopeService,
    _accountService_: any) => {
      $scope = $rootScope.$new();
      accountService = _accountService_;
      $componentController = _$componentController_;
      $q = _$q_;
  }));

  describe('data initialization', () => {

    it('gets all accounts, then adds wildcard and regions per account to vm', () => {
      let accounts = [ {name: 'prod'}, {name: 'test'} ];
      let details: any = {
        prod: { name: 'prod', regions: [ {name: 'us-east-1'}, {name: 'us-west-1'}] },
        test: { name: 'test', regions: [ {name: 'us-west-2'}, {name: 'eu-west-1'}] }
      };

      spyOn(accountService, 'listAccounts').and.returnValue($q.when(accounts));
      spyOn(accountService, 'getAccountDetails').and.callFake((accountName: string): ng.IPromise<any> => {
        return $q.when(details[accountName]);
      });

      initializeController(null);
      $ctrl.$onInit();
      $scope.$digest();

      expect($ctrl.accounts).toEqual([details.prod, details.test]);
      expect($ctrl.regionsByAccount).toEqual({
        prod: [ '*', 'us-east-1', 'us-west-1'],
        test: [ '*', 'us-west-2', 'eu-west-1']
      });
    });
  });


});
