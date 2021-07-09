import Spy = jasmine.Spy;
import { mock, IScope, IQService, IControllerService, IRootScopeService } from 'angular';

import { IgorService } from '../../../../ci/igor.service';
import { IJobConfig, IParameterDefinitionList } from '../../../../domain';
import { TRAVIS_STAGE, TravisStage } from './travisStage';

describe('Travis Stage Controller', () => {
  let $scope: IScope, $q: IQService, $ctrl: IControllerService;

  beforeEach(mock.module(TRAVIS_STAGE, require('angular-ui-bootstrap')));

  beforeEach(
    mock.inject(($controller: IControllerService, $rootScope: IRootScopeService, _$q_: IQService) => {
      $ctrl = $controller;
      $scope = $rootScope.$new();
      $q = _$q_;
    }),
  );

  const initialize = (stage: any): TravisStage => {
    return $ctrl(TravisStage, {
      stage,
      $scope,
    });
  };

  describe('updateJobsList', () => {
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
      expect(controller.viewState.jobsLoaded).toBe(true);
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
      expect(controller.viewState.jobsLoaded).toBe(true);
      expect((IgorService.listJobsForMaster as Spy).calls.count()).toBe(0);
    });

    it('gets jobs from igor and adds them to scope', () => {
      spyOn(IgorService, 'listJobsForMaster').and.returnValue($q.when(['a', 'b']));
      const stage = {
        master: 'not-parameterized',
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toEqual(['a', 'b']);
      expect(controller.viewState.jobsLoaded).toBe(true);
    });

    it('clears job if no longer present when retrieving from igor', () => {
      spyOn(IgorService, 'listJobsForMaster').and.returnValue($q.when(['a', 'b']));
      spyOn(IgorService, 'getJobConfig').and.returnValue($q.when(null));
      const stage = {
        master: 'not-parameterized',
        job: 'c',
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toEqual(['a', 'b']);
      expect(controller.viewState.jobsLoaded).toBe(true);
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
      expect(controller.viewState.jobsLoaded).toBe(true);
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
      expect(controller.viewState.jobsLoaded).toBe(true);
      expect((IgorService.listJobsForMaster as Spy).calls.count()).toBe(0);
      expect((IgorService.getJobConfig as Spy).calls.count()).toBe(0);
    });

    it('gets job config and adds parameters to scope, setting defaults if present and not overridden', () => {
      const params: IParameterDefinitionList[] = [
        { name: 'overridden', defaultValue: 'z' },
        { name: 'notSet', defaultValue: 'a' },
        { name: 'noDefault', defaultValue: null },
      ];
      const jobConfig = {
        parameterDefinitionList: params,
      } as IJobConfig;
      spyOn(IgorService, 'listJobsForMaster').and.returnValue($q.when(['a', 'b']));
      spyOn(IgorService, 'getJobConfig').and.returnValue($q.when(jobConfig));
      const stage = {
        master: 'not-parameterized',
        job: 'a',
        parameters: {
          overridden: 'f',
        },
      };
      const controller = initialize(stage);
      $scope.$digest();
      expect(controller.jobs).toEqual(['a', 'b']);
      expect(controller.viewState.jobsLoaded).toBe(true);
      expect(controller.stage.job).toBe('a');
      expect(controller.jobParams).toBe(params);
      expect(controller.useDefaultParameters.overridden).toBeUndefined();
      expect(controller.useDefaultParameters.notSet).toBe(true);
      expect(controller.useDefaultParameters.noDefault).toBeUndefined();
    });
  });
});
