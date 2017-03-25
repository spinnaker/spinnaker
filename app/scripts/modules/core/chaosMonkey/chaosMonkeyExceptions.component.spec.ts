import {mock, IComponentControllerService, IScope, IQService, IRootScopeService, IPromise} from 'angular';

import {CHAOS_MONKEY_EXCEPTIONS_COMPONENT, ChaosMonkeyExceptionsController} from './chaosMonkeyExceptions.component';
import {AccountService} from 'core/account/account.service';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from 'core/application/applicationModel.builder';
import {ChaosMonkeyConfig} from 'core/chaosMonkey/chaosMonkeyConfig.component';

describe('Controller: ChaosMonkeyExceptions', () => {

  let $componentController: IComponentControllerService,
      accountService: AccountService,
      $ctrl: ChaosMonkeyExceptionsController,
      $scope: IScope,
      $q: IQService,
      applicationBuilder: ApplicationModelBuilder;

  let initializeController = (data: any) => {
    $ctrl = <ChaosMonkeyExceptionsController> $componentController(
      'chaosMonkeyExceptions',
      { $scope: null, accountService: accountService, $q: $q },
      data
    );
  };

  beforeEach(mock.module(APPLICATION_MODEL_BUILDER, CHAOS_MONKEY_EXCEPTIONS_COMPONENT));

  beforeEach(mock.inject((
    _$componentController_: IComponentControllerService,
    _$q_: IQService,
    $rootScope: IRootScopeService,
    _accountService_: AccountService,
    _applicationModelBuilder_: ApplicationModelBuilder) => {
      $scope = $rootScope.$new();
      accountService = _accountService_;
      $componentController = _$componentController_;
      $q = _$q_;
      applicationBuilder = _applicationModelBuilder_;
  }));

  describe('data initialization', () => {

    it('gets all accounts, then adds wildcard and regions per account to vm', () => {
      let accounts = [ {name: 'prod'}, {name: 'test'} ];
      let details: any = {
        prod: { name: 'prod', regions: [ {name: 'us-east-1'}, {name: 'us-west-1'}] },
        test: { name: 'test', regions: [ {name: 'us-west-2'}, {name: 'eu-west-1'}] }
      };

      spyOn(accountService, 'listAccounts').and.returnValue($q.when(accounts));
      spyOn(accountService, 'getAccountDetails').and.callFake((accountName: string): IPromise<any> => {
        return $q.when(details[accountName]);
      });

      initializeController(null);
      $ctrl.application =
        applicationBuilder.createApplication({key: 'serverGroups', data: [], ready: () => $q.when(null), loaded: true});
      $ctrl.config = new ChaosMonkeyConfig($ctrl.application.attributes.chaosMonkey || {});

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
