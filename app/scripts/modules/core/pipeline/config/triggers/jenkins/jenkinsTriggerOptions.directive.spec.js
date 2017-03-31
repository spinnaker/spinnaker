'use strict';

describe('Jenkins Trigger: JenkinsTriggerOptionsCtrl', function() {

  var $scope, igorService, ctrl, $q, command;

  beforeEach(
    window.module(
      require('./jenkinsTriggerOptions.directive.js')
    )
  );

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$q
  beforeEach(
    window.module(($qProvider) => {
      $qProvider.errorOnUnhandledRejections(false);
  }));

  beforeEach(window.inject(function($rootScope, _igorService_, $controller, _$q_) {
    $scope = $rootScope.$new();
    igorService = _igorService_;
    $q = _$q_;

    command = {
      trigger: {
        type: 'jenkins',
        master: 'a',
        job: 'b'
      }
    };

    this.initialize = function() {
      ctrl = $controller('JenkinsTriggerOptionsCtrl', {
        igorService: igorService,
        $scope: $scope,
      }, { command: command });
    };
  }));

  it('loads jobs on initialization, setting state flags', function () {
    let builds = [];
    spyOn(igorService, 'listBuildsForJob').and.returnValue($q.when(builds));

    this.initialize();
    expect(ctrl.viewState.buildsLoading).toBe(true);
    $scope.$digest();
    expect(ctrl.viewState.buildsLoading).toBe(false);
    expect(ctrl.viewState.loadError).toBe(false);
    expect(ctrl.builds).toEqual(builds);
    expect(ctrl.viewState.selectedBuild).toBe(null);
  });

  it('sets build to first one available when returned on initialization', function () {
    let build = { number: '1', result: 'SUCCESS' };
    spyOn(igorService, 'listBuildsForJob').and.returnValue($q.when([build]));

    this.initialize();
    expect(ctrl.viewState.buildsLoading).toBe(true);
    $scope.$digest();
    expect(ctrl.viewState.buildsLoading).toBe(false);
    expect(ctrl.viewState.loadError).toBe(false);
    expect(ctrl.builds).toEqual([build]);
    expect(ctrl.viewState.selectedBuild).toBe(build);
    expect(command.extraFields.buildNumber).toBe('1');
  });

  it('sets flags when build load fails', function () {
    spyOn(igorService, 'listBuildsForJob').and.returnValue($q.reject('igored'));

    this.initialize();
    expect(ctrl.viewState.buildsLoading).toBe(true);
    $scope.$digest();
    expect(ctrl.viewState.buildsLoading).toBe(false);
    expect(ctrl.viewState.loadError).toBe(true);
    expect(ctrl.builds).toBeUndefined();
    expect(ctrl.viewState.selectedBuild).toBe(null);
    expect(command.extraFields.buildNumber).toBeUndefined();
  });

  it('re-initializes when trigger changes', function () {
    let firstBuild = { number: '1', result: 'SUCCESS' },
        secondBuild = { number: '3', result: 'SUCCESS'},
        secondTrigger = { type: 'jenkins', master: 'b', job: 'c'};

    spyOn(igorService, 'listBuildsForJob').and.callFake((master, job) => {
      let builds = [];
      if (job === 'b') {
        builds = [firstBuild];
      }
      if (job === 'c') {
        builds = [secondBuild];
      }
      return $q.when(builds);
    });

    this.initialize();
    $scope.$digest();

    expect(ctrl.builds).toEqual([firstBuild]);
    expect(ctrl.viewState.selectedBuild).toBe(firstBuild);
    expect(command.extraFields.buildNumber).toBe('1');

    command.trigger = secondTrigger;
    $scope.$digest();

    expect(ctrl.builds).toEqual([secondBuild]);
    expect(ctrl.viewState.selectedBuild).toBe(secondBuild);
    expect(command.extraFields.buildNumber).toBe('3');
  });

});
