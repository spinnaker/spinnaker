import { mock, IComponentControllerService, IScope, IQService, IRootScopeService } from 'angular';

import { CHAOS_MONKEY_EXCEPTIONS_COMPONENT, ChaosMonkeyExceptionsController } from './chaosMonkeyExceptions.component';
import { AccountService } from 'core/account/AccountService';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';
import { ChaosMonkeyConfig } from 'core/chaosMonkey/chaosMonkeyConfig.component';

describe('Controller: ChaosMonkeyExceptions', () => {
  let $componentController: IComponentControllerService,
    $ctrl: ChaosMonkeyExceptionsController,
    $scope: IScope,
    $q: IQService,
    applicationBuilder: ApplicationModelBuilder;

  const initializeController = (data: any) => {
    $ctrl = $componentController(
      'chaosMonkeyExceptions',
      { $scope: null, $q },
      data,
    ) as ChaosMonkeyExceptionsController;
  };

  beforeEach(mock.module(APPLICATION_MODEL_BUILDER, CHAOS_MONKEY_EXCEPTIONS_COMPONENT));

  beforeEach(
    mock.inject(
      (
        _$componentController_: IComponentControllerService,
        _$q_: IQService,
        $rootScope: IRootScopeService,
        _applicationModelBuilder_: ApplicationModelBuilder,
      ) => {
        $scope = $rootScope.$new();
        $componentController = _$componentController_;
        $q = _$q_;
        applicationBuilder = _applicationModelBuilder_;
      },
    ),
  );

  describe('data initialization', () => {
    it('gets all accounts, then adds wildcard and regions per account to vm', () => {
      const accounts: any = [
        { name: 'prod', regions: [{ name: 'us-east-1' }, { name: 'us-west-1' }] },
        { name: 'test', regions: [{ name: 'us-west-2' }, { name: 'eu-west-1' }] },
      ];

      spyOn(AccountService, 'listAllAccounts').and.returnValue($q.when(accounts));

      initializeController(null);
      $ctrl.application = applicationBuilder.createApplication('app', {
        key: 'serverGroups',
        loader: () => $q.resolve([]),
        onLoad: (_app, data) => $q.resolve(data),
      });
      $ctrl.application.serverGroups.refresh();
      $scope.$digest();

      $ctrl.config = new ChaosMonkeyConfig($ctrl.application.attributes.chaosMonkey || {});

      $ctrl.$onInit();
      $scope.$digest();

      expect($ctrl.accounts).toEqual([accounts[0], accounts[1]]);
      expect($ctrl.regionsByAccount).toEqual({
        prod: ['*', 'us-east-1', 'us-west-1'],
        test: ['*', 'us-west-2', 'eu-west-1'],
      });
    });
  });
});
