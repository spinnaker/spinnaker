import { IComponentControllerService, IQService, IRootScopeService, IScope, mock } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';
import { StateService } from '@uirouter/angularjs';

import { AccountService } from 'core/account/account.service';
import { APPLICATIONS_COMPONENT, ApplicationsController } from './applications.component';
import { ApplicationReader, IApplicationSummary } from 'core/application/service/application.read.service';

describe('Controller: Applications', () => {
  beforeEach(
    mock.module(
      APPLICATIONS_COMPONENT,
      require('angular-ui-bootstrap')
    )
  );

  describe('filtering', () => {

    const deck = { name: 'deck', email: 'a@netflix.com', createTs: String(new Date(2).getTime()) } as IApplicationSummary,
        oort = { name: 'oort', email: 'b@netflix.com', createTs: String(new Date(3).getTime()) } as IApplicationSummary,
        mort = { name: 'mort', email: 'c@netflix.com', createTs: String(new Date(1).getTime()) } as IApplicationSummary,
        applicationList = [ deck, oort, mort ] as IApplicationSummary[];

    let $scope: IScope,
        $q: IQService,
        accountService: AccountService,
        applicationReader: ApplicationReader,
        ctrl: ApplicationsController;

    // Initialize the controller and a mock scope
    beforeEach(mock.inject((
                            $componentController: IComponentControllerService,
                            $rootScope: IRootScopeService,
                            _$q_: IQService,
                            $uibModal: IModalService,
                            _accountService_: AccountService,
                            $state: StateService,
                            _applicationReader_: ApplicationReader) => {

      $scope = $rootScope.$new();
      $q = _$q_;
      accountService = _accountService_;
      applicationReader = _applicationReader_;

      spyOn(applicationReader, 'listApplications').and.callFake(() => {
        return $q.when(applicationList);
      });

      spyOn(accountService, 'listAccounts').and.callFake(() => {
        return $q.when([]);
      });

      ctrl = $componentController('applications', {
        $scope: $scope,
        $uibModal: $uibModal,
        accountService: accountService,
        $state: $state,
      }) as ApplicationsController;
      $scope.viewState.sortModel.key = 'name';
    }));

    it('sets applicationsLoaded flag when applications retrieved and added to scope', () => {
      expect(ctrl.applicationsLoaded).toBe(false);
      expect(ctrl.applications).toBeUndefined();

      $scope.$digest();

      expect(ctrl.applicationsLoaded).toBe(true);
      expect(ctrl.applications).toBe(applicationList);
      expect(ctrl.filteredApplications).toEqual([deck, mort, oort]);

    });

    it('filters applications by name or email', () => {
      $scope.viewState.applicationFilter = 'a@netflix.com';
      $scope.$digest();
      expect(ctrl.applications).toBe(applicationList);
      expect(ctrl.filteredApplications).toEqual([deck]);

      $scope.viewState.applicationFilter = 'ort';
      ctrl.filterApplications();
      expect(ctrl.filteredApplications).toEqual([mort, oort]);
    });

    it('sorts and filters applications', () => {
      $scope.viewState.sortModel.key = '-name';
      $scope.$digest();
      expect(ctrl.filteredApplications).toEqual([oort, mort, deck]);

      $scope.viewState.sortModel.key = '-createTs';
      ctrl.filterApplications();
      expect(ctrl.filteredApplications).toEqual([oort, deck, mort]);

      $scope.viewState.sortModel.key = 'createTs';
      ctrl.filterApplications();
      expect(ctrl.filteredApplications).toEqual([mort, deck, oort]);

      $scope.viewState.applicationFilter = 'ort';
      ctrl.filterApplications();
      expect(ctrl.filteredApplications).toEqual([mort, oort]);
    });
  });
});
