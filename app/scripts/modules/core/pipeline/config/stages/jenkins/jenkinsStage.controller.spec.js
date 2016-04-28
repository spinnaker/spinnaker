'use strict';

describe('Jenkins Stage Controller', function () {
  var scope, igorService, $q;

  beforeEach(
    window.module(
      require('./jenkinsStage')
    )
  );

  beforeEach(
    window.inject(function($controller, $rootScope, _igorService_, _$q_) {
      igorService = _igorService_;
      scope = $rootScope.$new();
      $q = _$q_;
      this.initialize = function(stage) {
        $controller('JenkinsStageCtrl', {
          $scope: scope,
          stage: stage,
          igorService: igorService,
        });
      };
    })
  );

  describe('updateJobsList', function () {

    beforeEach(function() {
      spyOn(igorService, 'listMasters').and.returnValue($q.when([]));
    });

    it('does nothing if master is parameterized', function () {
      spyOn(igorService, 'listJobsForMaster');
      let stage = {
        master: '${parameter.master}'
      };
      this.initialize(stage);
      scope.$digest();
      expect(scope.jobs).toBeUndefined();
      expect(scope.viewState.jobsLoaded).toBe(true);
      expect(igorService.listJobsForMaster.calls.count()).toBe(0);
    });

    it('does nothing if job is parameterized', function() {
      spyOn(igorService, 'listJobsForMaster');
      let stage = {
        master: 'not-parameterized',
        job: '${parameter.job}'
      };
      this.initialize(stage);
      scope.$digest();
      expect(scope.jobs).toBeUndefined();
      expect(scope.viewState.jobsLoaded).toBe(true);
      expect(igorService.listJobsForMaster.calls.count()).toBe(0);
    });

    it('gets jobs from igor and adds them to scope', function() {
      spyOn(igorService, 'listJobsForMaster').and.returnValue($q.when(['a', 'b']));
      let stage = {
        master: 'not-parameterized',
      };
      this.initialize(stage);
      scope.$digest();
      expect(scope.jobs).toEqual(['a', 'b']);
      expect(scope.viewState.jobsLoaded).toBe(true);
    });

    it('clears job if no longer present when retrieving from igor', function() {
      spyOn(igorService, 'listJobsForMaster').and.returnValue($q.when(['a', 'b']));
      spyOn(igorService, 'getJobConfig').and.returnValue($q.when(null));
      let stage = {
        master: 'not-parameterized',
        job: 'c',
      };
      this.initialize(stage);
      scope.$digest();
      expect(scope.jobs).toEqual(['a', 'b']);
      expect(scope.viewState.jobsLoaded).toBe(true);
      expect(stage.job).toBe('');
    });

  });

  describe('updateJobConfig', function () {

    beforeEach(function() {
      spyOn(igorService, 'listMasters').and.returnValue($q.when([]));
    });

    it('does nothing if master is parameterized', function () {
      spyOn(igorService, 'listJobsForMaster');
      spyOn(igorService, 'getJobConfig');
      let stage = {
        master: '${parameter.master}'
      };
      this.initialize(stage);
      scope.$digest();
      expect(scope.jobs).toBeUndefined();
      expect(scope.viewState.jobsLoaded).toBe(true);
      expect(igorService.listJobsForMaster.calls.count()).toBe(0);
      expect(igorService.getJobConfig.calls.count()).toBe(0);
    });

    it('does nothing if job is parameterized', function () {
      spyOn(igorService, 'listJobsForMaster');
      spyOn(igorService, 'getJobConfig');
      let stage = {
        master: 'not-parameterized',
        job: '${parameter.job}'
      };
      this.initialize(stage);
      scope.$digest();
      expect(scope.jobs).toBeUndefined();
      expect(scope.viewState.jobsLoaded).toBe(true);
      expect(igorService.listJobsForMaster.calls.count()).toBe(0);
      expect(igorService.getJobConfig.calls.count()).toBe(0);
    });

    it('gets job config and adds parameters to scope, setting defaults if present and not overridden', function () {
      let params = [
        {name: 'overridden', defaultValue: 'z'},
        {name: 'notSet', defaultValue: 'a'},
        {name: 'noDefault', defaultValue: null}
      ];
      let jobConfig = {
        parameterDefinitionList: params
      };
      spyOn(igorService, 'listJobsForMaster').and.returnValue($q.when(['a', 'b']));
      spyOn(igorService, 'getJobConfig').and.returnValue($q.when(jobConfig));
      let stage = {
        master: 'not-parameterized',
        job: 'a',
        parameters: {
          overridden: 'f'
        },
      };
      this.initialize(stage);
      scope.$digest();
      expect(scope.jobs).toEqual(['a', 'b']);
      expect(scope.viewState.jobsLoaded).toBe(true);
      expect(stage.job).toBe('a');
      expect(scope.jobParams).toBe(params);
      expect(scope.useDefaultParameters.overridden).toBeUndefined();
      expect(scope.useDefaultParameters.notSet).toBe(true);
      expect(scope.useDefaultParameters.noDefault).toBeUndefined();
    });
  });
});
