import {mock, IScope, IQProvider, IQService, IControllerService, IRootScopeService} from 'angular';

import {IgorService} from 'core/ci/igor.service';
import {IBuild} from 'core/domain/IBuild';
import {IBuildTrigger} from 'core/domain/ITrigger';
import {TRAVIS_TRIGGER_OPTIONS_COMPONENT, TravisTriggerOptions} from './travisTriggerOptions.component';

interface ICommand {
  trigger: IBuildTrigger;
  extraFields?: any;
}

describe('Travis Trigger: TravisTriggerOptionsCtrl', () => {

  let $scope: IScope,
    igorService: IgorService,
    $ctrl: IControllerService,
    $q: IQService,
    command: ICommand;

  beforeEach(mock.module(TRAVIS_TRIGGER_OPTIONS_COMPONENT));

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$q
  beforeEach(
    mock.module(($qProvider: IQProvider) => {
      $qProvider.errorOnUnhandledRejections(false);
  }));

  beforeEach(mock.inject(($rootScope: IRootScopeService, _igorService_: IgorService, $controller: IControllerService, _$q_: IQService) => {
    $ctrl = $controller;
    $scope = $rootScope.$new();
    igorService = _igorService_;
    $q = _$q_;

    command = {
      trigger: <IBuildTrigger>{
        type: 'travis',
        master: 'a',
        job: 'b'
      }
    };
  }));

  const initialize = () => {
    return $ctrl(TravisTriggerOptions, {
      igorService: igorService,
      $scope: $scope,
    }, {command: command});
  };

  it('loads jobs on initialization, setting state flags', () => {
    let builds: IBuild[] = [];
    spyOn(igorService, 'listBuildsForJob').and.returnValue($q.when(builds));

    const controller = initialize();
    expect(controller.viewState.buildsLoading).toBe(true);
    $scope.$digest();
    expect(controller.viewState.buildsLoading).toBe(false);
    expect(controller.viewState.loadError).toBe(false);
    expect(controller.builds).toEqual(builds);
    expect(controller.viewState.selectedBuild).toBe(null);
  });

  it('sets build to first one available when returned on initialization', function () {
    let build = {number: '1', result: 'SUCCESS'};
    spyOn(igorService, 'listBuildsForJob').and.returnValue($q.when([build]));

    const controller = initialize();
    expect(controller.viewState.buildsLoading).toBe(true);
    $scope.$digest();
    expect(controller.viewState.buildsLoading).toBe(false);
    expect(controller.viewState.loadError).toBe(false);
    expect(controller.builds).toEqual([build]);
    expect(controller.viewState.selectedBuild).toBe(build);
    expect(command.extraFields.buildNumber).toBe('1');
  });

  it('sets flags when build load fails', function () {
    spyOn(igorService, 'listBuildsForJob').and.returnValue($q.reject('igored'));

    const controller = initialize();
    expect(controller.viewState.buildsLoading).toBe(true);
    $scope.$digest();
    expect(controller.viewState.buildsLoading).toBe(false);
    expect(controller.viewState.loadError).toBe(true);
    expect(controller.builds).toBeUndefined();
    expect(controller.viewState.selectedBuild).toBe(null);
    expect(command.extraFields.buildNumber).toBeUndefined();
  });

  it('re-initializes when trigger changes', function () {
    let firstBuild: IBuild = <any>{number: '1', result: 'SUCCESS'},
      secondBuild: IBuild = <any>{number: '3', result: 'SUCCESS'},
      secondTrigger: IBuildTrigger = <IBuildTrigger>{type: 'travis', master: 'b', job: 'c'};

    spyOn(igorService, 'listBuildsForJob').and.callFake((_master: string, job: string) => {
      let builds: IBuild[] = [];
      if (job === 'b') {
        builds = [firstBuild];
      }
      if (job === 'c') {
        builds = [secondBuild];
      }
      return $q.when(builds);
    });

    const controller = initialize();
    $scope.$digest();

    expect(controller.builds).toEqual([firstBuild]);
    expect(controller.viewState.selectedBuild).toBe(firstBuild);
    expect(command.extraFields.buildNumber).toBe('1');

    command.trigger = secondTrigger;
    $scope.$digest();

    expect(controller.builds).toEqual([secondBuild]);
    expect(controller.viewState.selectedBuild).toBe(secondBuild);
    expect(command.extraFields.buildNumber).toBe('3');
  });

});
