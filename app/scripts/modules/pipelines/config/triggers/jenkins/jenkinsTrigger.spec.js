'use strict';

describe('Controller: jenkinsTrigger', function() {

  beforeEach(
    window.module(
      require('./jenkinsTrigger.module.js'),
      require('../../../../utils/lodash.js')
    )
  );

  beforeEach(window.inject(function ($controller, $rootScope, $q, igorService, ___) {
    this._ = ___;
    this.$q = $q;
    this.igorService = igorService;
    this.infrastructureCaches = {
      buildMasters: { getStats: function() { return { ageMax: 1 }; } },
      buildJobs: { getStats: function() { return { ageMax: 1 }; } }
    };
    this.$scope = $rootScope.$new();
    this.initializeController = function (trigger) {
      this.controller = $controller('JenkinsTriggerCtrl', {
        $scope: this.$scope,
        trigger: trigger,
        igorService: this.igorService,
        infrastructureCaches: this.infrastructureCaches,
      });
    };
  }));

  describe('updateJobsList', function() {
    it('gets list of jobs when initialized with a trigger with a master and sets loading states', function() {
      var $q = this.$q,
          $scope = this.$scope,
          jobs = ['some_job', 'some_other_job'],
          trigger = {master: 'jenkins', job: 'some_job'};

      spyOn(this.igorService, 'listJobsForMaster').and.returnValue($q.when(jobs));
      spyOn(this.igorService, 'listMasters').and.returnValue($q.when(['jenkins']));
      this.initializeController(trigger);
      expect($scope.viewState.jobsLoaded).toBe(false);
      expect($scope.viewState.mastersLoaded).toBe(false);
      $scope.$digest();

      expect($scope.jobs).toBe(jobs);
      expect($scope.masters).toEqual(['jenkins']);
      expect($scope.viewState.jobsLoaded).toBe(true);
      expect($scope.viewState.mastersLoaded).toBe(true);
    });

    it('updates jobs list when master changes, preserving job if present in both masters', function() {
      var masterA = {
          name: 'masterA',
          jobs: ['a', 'b']
        },
        masterB = {
          name: 'masterB',
          jobs: ['b', 'c']
        },
        trigger = {
          master: 'masterA',
          job: 'a'
        },
        $scope = this.$scope,
        $q = this.$q;

      spyOn(this.igorService, 'listJobsForMaster').and.callFake(function() {
        return $q.when(_.find([masterA, masterB], {name: $scope.trigger.master}).jobs);
      });
      spyOn(this.igorService, 'listMasters').and.returnValue($q.when(['masterA', 'masterB']));
      this.initializeController(trigger);
      $scope.$digest();

      expect($scope.jobs).toBe(masterA.jobs);

      // Change master, job no longer available, trigger job should be removed
      trigger.master = 'masterB';
      $scope.$digest();
      expect(trigger.job).toBe('');
      expect($scope.jobs).toBe(masterB.jobs);

      // Select job in both masters; jobs should not change
      trigger.job = 'b';
      $scope.$digest();
      expect(trigger.job).toBe('b');
      expect($scope.jobs).toBe(masterB.jobs);

      // Change master, trigger job should remain
      trigger.master = 'masterA';
      $scope.$digest();
      expect(trigger.job).toBe('b');
      expect($scope.jobs).toBe(masterA.jobs);
    });

    it('retains current job if no jobs found in master because that is probably a server-side issue', function() {
      var masterA = {
          name: 'masterA',
          jobs: []
        },
        trigger = {
          master: 'masterA',
          job: 'a'
        },
        $scope = this.$scope,
        $q = this.$q;

      spyOn(this.igorService, 'listJobsForMaster').and.callFake(function() {
        return $q.when([]);
      });
      spyOn(this.igorService, 'listMasters').and.returnValue($q.when(['masterA']));
      this.initializeController(trigger);
      $scope.$digest();

      expect(trigger.job).toBe('a');
    });
  });

});
