import {mock, IScope, IQService, IControllerService, IRootScopeService} from 'angular';
import {find} from 'lodash';

import {IgorService} from 'core/ci/igor.service';
import {IBuildTrigger} from 'core/domain/ITrigger';
import {TRAVIS_TRIGGER, TravisTrigger} from './travisTrigger.module';

describe('Controller: travisTrigger', () => {
  let $scope: IScope,
    igorService: IgorService,
    $q: IQService,
    $ctrl: IControllerService;

  beforeEach(mock.module(TRAVIS_TRIGGER));

  beforeEach(
    mock.inject(($controller: IControllerService, $rootScope: IRootScopeService, _$q_: IQService, _igorService_: IgorService) => {
      $ctrl = $controller;
      $q = _$q_;
      igorService = _igorService_;
      $scope = $rootScope.$new();
    }));

  const initializeController = (trigger: IBuildTrigger): TravisTrigger => {
    return $ctrl(TravisTrigger, {
      trigger,
      $scope,
      igorService
    });
  };

  describe('updateJobsList', () => {
    it('gets list of jobs when initialized with a trigger with a master and sets loading states', () => {
      const jobs = ['some_job', 'some_other_job'],
        trigger = <IBuildTrigger>{master: 'travis', job: 'some_job'};

      spyOn(igorService, 'listJobsForMaster').and.returnValue($q.when(jobs));
      spyOn(igorService, 'listMasters').and.returnValue($q.when(['travis']));
      const controller = initializeController(trigger);
      expect(controller.viewState.jobsLoaded).toBe(false);
      expect(controller.viewState.mastersLoaded).toBe(false);
      $scope.$digest();
      expect(controller.jobs).toBe(jobs);
      expect(controller.masters).toEqual(['travis']);
      expect(controller.viewState.jobsLoaded).toBe(true);
      expect(controller.viewState.mastersLoaded).toBe(true);
    });

    it('updates jobs list when master changes, preserving job if present in both masters', () => {
      const masterA = {
          name: 'masterA',
          jobs: ['a', 'b']
        },
        masterB = {
          name: 'masterB',
          jobs: ['b', 'c']
        },
        trigger = <IBuildTrigger>{
          master: 'masterA',
          job: 'a'
        };

      spyOn(igorService, 'listJobsForMaster').and.callFake((master: string) => {
        return $q.when(find([masterA, masterB], {name: master}).jobs);
      });
      spyOn(igorService, 'listMasters').and.returnValue($q.when(['masterA', 'masterB']));

      const controller = initializeController(trigger);
      $scope.$digest();

      expect(controller.jobs).toBe(masterA.jobs);

      // Change master, job no longer available, trigger job should be removed
      trigger.master = 'masterB';
      $scope.$digest();
      expect(trigger.job).toBe('');
      expect(controller.jobs).toBe(masterB.jobs);

      // Select job in both masters; jobs should not change
      trigger.job = 'b';
      $scope.$digest();
      expect(trigger.job).toBe('b');
      expect(controller.jobs).toBe(masterB.jobs);

      // Change master, trigger job should remain
      trigger.master = 'masterA';
      $scope.$digest();
      expect(trigger.job).toBe('b');
      expect(controller.jobs).toBe(masterA.jobs);
    });

    it('retains current job if no jobs found in master because that is probably a server-side issue', () => {
      const trigger = <IBuildTrigger>{
        master: 'masterA',
        job: 'a'
      };

      spyOn(igorService, 'listJobsForMaster').and.callFake(() => {
        return $q.when([]);
      });
      spyOn(igorService, 'listMasters').and.returnValue($q.when(['masterA']));
      initializeController(trigger);
      $scope.$digest();

      expect(trigger.job).toBe('a');
    });
  });
});
