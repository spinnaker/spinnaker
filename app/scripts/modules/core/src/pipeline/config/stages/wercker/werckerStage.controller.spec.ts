import Spy = jasmine.Spy;
import { mock, IScope, IQService, IControllerService, IRootScopeService } from 'angular';

import { IgorService } from '../../../../ci/igor.service';
import { IJobConfig, IParameterDefinitionList } from '../../../../domain';
import { WERCKER_STAGE, WerckerStage } from './werckerStage';

describe('Wercker Stage Controller', () => {
  let $scope: IScope, $q: IQService, $ctrl: IControllerService;

  beforeEach(mock.module(WERCKER_STAGE, require('angular-ui-bootstrap')));

  beforeEach(
    mock.inject(($controller: IControllerService, $rootScope: IRootScopeService, _$q_: IQService) => {
      $ctrl = $controller;
      $scope = $rootScope.$new();
      $q = _$q_;
    }),
  );

  const initialize = (stage: any): WerckerStage => {
    return $ctrl(WerckerStage, {
      stage,
      $scope,
    });
  };

  describe('updateAppsList', () => {
    beforeEach(() => {
      spyOn(IgorService, 'listMasters').and.returnValue($q.when([]));
    });

    it('does nothing if master is parameterized', () => {
      spyOn(IgorService, 'listJobsForMaster');
      const stage = {
        master: '${parameter.master}',
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toBeUndefined();
      expect(controller.viewState.appsLoaded).toBe(true);
      expect((IgorService.listJobsForMaster as Spy).calls.count()).toBe(0);
    });

    it('does nothing if job is parameterized', () => {
      spyOn(IgorService, 'listJobsForMaster');
      const stage = {
        master: 'not-parameterized',
        job: '${parameter.job}',
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toBeUndefined();
      expect(controller.viewState.appsLoaded).toBe(true);
      expect((IgorService.listJobsForMaster as Spy).calls.count()).toBe(0);
    });

    it('gets jobs from igor and adds them to scope', () => {
      const jobs = ['type/org/app/p1', 'type/org/app/p2'];
      spyOn(IgorService, 'listJobsForMaster').and.returnValue($q.when(jobs));
      const stage = {
        master: 'not-parameterized',
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toEqual(jobs);
      expect(controller.viewState.appsLoaded).toBe(true);
    });

    it('clears job if no longer present when retrieving from igor', () => {
      const jobs = ['type/org/app/a', 'type/org/app/b'];
      spyOn(IgorService, 'listJobsForMaster').and.returnValue($q.when(jobs));
      spyOn(IgorService, 'getJobConfig').and.returnValue($q.when(null));
      const stage = {
        master: 'not-parameterized',
        job: 'type/org/app/c',
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toEqual(jobs);
      expect(controller.viewState.appsLoaded).toBe(true);
      expect(stage.job).toBe('');
    });
  });

  describe('updateJobConfig', () => {
    beforeEach(() => {
      spyOn(IgorService, 'listMasters').and.returnValue($q.when([]));
    });

    it('does nothing if master is parameterized', () => {
      spyOn(IgorService, 'listJobsForMaster');
      spyOn(IgorService, 'getJobConfig');
      const stage = {
        master: '${parameter.master}',
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toBeUndefined();
      expect(controller.viewState.appsLoaded).toBe(true);
      expect((IgorService.listJobsForMaster as Spy).calls.count()).toBe(0);
      expect((IgorService.getJobConfig as Spy).calls.count()).toBe(0);
    });

    it('does nothing if job is parameterized', () => {
      spyOn(IgorService, 'listJobsForMaster');
      spyOn(IgorService, 'getJobConfig');
      const stage = {
        master: 'not-parameterized',
        job: '${parameter.job}',
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toBeUndefined();
      expect(controller.viewState.appsLoaded).toBe(true);
      expect((IgorService.listJobsForMaster as Spy).calls.count()).toBe(0);
      expect((IgorService.getJobConfig as Spy).calls.count()).toBe(0);
    });

    it('gets job config and adds parameters to scope, setting defaults if present and not overridden', () => {
      const jobs = ['type/org/app/x', 'type/org/app/y'];
      const params: IParameterDefinitionList[] = [
        { name: 'overridden', defaultValue: 'z' },
        { name: 'notSet', defaultValue: 'a' },
        { name: 'noDefault', defaultValue: null },
      ];
      const jobConfig = {
        parameterDefinitionList: params,
      } as IJobConfig;
      spyOn(IgorService, 'listJobsForMaster').and.returnValue($q.when(jobs));
      spyOn(IgorService, 'getJobConfig').and.returnValue($q.when(jobConfig));
      const stage = {
        master: 'not-parameterized',
        app: 'org/app',
        job: 'type/org/app/x',
        parameters: {
          overridden: 'f',
        },
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toEqual(jobs);
      expect(controller.viewState.appsLoaded).toBe(true);
      expect(controller.stage.job).toBe('type/org/app/x');
      expect(controller.jobParams).toBe(params);
      expect(controller.useDefaultParameters.overridden).toBeUndefined();
      expect(controller.useDefaultParameters.notSet).toBe(true);
      expect(controller.useDefaultParameters.noDefault).toBeUndefined();
    });
  });
});
