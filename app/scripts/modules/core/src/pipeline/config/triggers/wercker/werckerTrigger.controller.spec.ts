import { mock, IScope, IQService, IControllerService, IRootScopeService } from 'angular';
import { find } from 'lodash';

import { IgorService } from 'core/ci/igor.service';
import { IWerckerTrigger } from 'core/domain/ITrigger';
import { WERCKER_TRIGGER, WerckerTrigger } from './werckerTrigger.module';

describe('Controller: werckerTrigger', () => {
  let $scope: IScope, $q: IQService, $ctrl: IControllerService;

  beforeEach(mock.module(WERCKER_TRIGGER));

  beforeEach(
    mock.inject(($controller: IControllerService, $rootScope: IRootScopeService, _$q_: IQService) => {
      $ctrl = $controller;
      $q = _$q_;
      $scope = $rootScope.$new();
    }),
  );

  const initializeController = (trigger: IWerckerTrigger): WerckerTrigger => {
    return $ctrl(WerckerTrigger, {
      trigger,
      $scope,
    });
  };

  describe('updateAppsList', () => {
    it('gets list of jobs when initialized with a wercker master and sets loading states', () => {
      const jobs = ['type/org/app/p1', 'type/org/app/p2'],
        trigger = { master: 'wercker', job: 'type/org/app/some_job' } as IWerckerTrigger;

      spyOn(IgorService, 'listJobsForMaster').and.returnValue($q.when(jobs));
      spyOn(IgorService, 'listMasters').and.returnValue($q.when(['wercker']));
      const controller = initializeController(trigger);
      expect(controller.viewState.appsLoaded).toBe(false);
      expect(controller.viewState.mastersLoaded).toBe(false);
      $scope.$digest();
      expect(controller.jobs).toBe(jobs);
      expect(controller.masters).toEqual(['wercker']);
      expect(controller.viewState.appsLoaded).toBe(true);
      expect(controller.viewState.mastersLoaded).toBe(true);
    });

    it('updates jobs list when master changes, preserving job if present in both masters', () => {
      const masterA = {
          name: 'masterA',
          jobs: ['type/org/app/a', 'type/org/app/b'],
        },
        masterB = {
          name: 'masterB',
          jobs: ['type/org/app/b', 'type/org/app/c'],
        },
        trigger = {
          master: 'masterA',
          job: 'type/org/app/a',
        } as IWerckerTrigger;

      spyOn(IgorService, 'listJobsForMaster').and.callFake((master: string) => {
        return $q.when(find([masterA, masterB], { name: master }).jobs);
      });
      spyOn(IgorService, 'listMasters').and.returnValue($q.when(['masterA', 'masterB']));

      const controller = initializeController(trigger);
      $scope.$digest();

      expect(controller.jobs).toBe(masterA.jobs);

      // Change master, job no longer available, trigger job should be removed
      trigger.master = 'masterB';
      $scope.$digest();
      expect(trigger.job).toBe('');
      expect(controller.jobs).toBe(masterB.jobs);

      // Select job in both masters; jobs should not change
      trigger.app = 'org/app';
      trigger.pipeline = 'b';
      trigger.job = 'org/app/b';
      $scope.$digest();
      expect(trigger.job).toBe('org/app/b');
      expect(controller.jobs).toBe(masterB.jobs);

      // Change master, trigger job should remain
      trigger.master = 'masterA';
      $scope.$digest();
      expect(trigger.job).toBe('org/app/b');
      expect(controller.jobs).toBe(masterA.jobs);
    });

    it('retains current job if no jobs found in master because that is probably a server-side issue', () => {
      const trigger = {
        master: 'masterA',
        job: 'a',
      } as IWerckerTrigger;

      spyOn(IgorService, 'listJobsForMaster').and.callFake(() => {
        return $q.when([]);
      });
      spyOn(IgorService, 'listMasters').and.returnValue($q.when(['masterA']));
      initializeController(trigger);
      $scope.$digest();

      expect(trigger.job).toBe('a');
    });
  });
});
