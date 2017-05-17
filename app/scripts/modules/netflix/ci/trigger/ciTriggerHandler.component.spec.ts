import { ICompileService, IHttpBackendService, IRootScopeService, mock } from 'angular';

import { SETTINGS, TIME_FORMATTERS } from '@spinnaker/core';

import { IBranch, ICommit, ITag, ScmReader } from 'netflix/ci/services/scm.read.service';
import {
  IViewState,
  NETFLIX_CI_TRIGGER_HANDLER_COMPONENT,
  NetflixCiTriggerHandlerController
} from './ciTriggerHandler.component';

describe('CiTriggerHandler', () => {

  const SCM_BASE_URL = `${SETTINGS.gateUrl}/scm`;
  let $compile: ICompileService;
  let $scope: IRootScopeService;
  let $http: IHttpBackendService;
  let scmReader: ScmReader;
  let elem: JQuery;
  let ctrl: NetflixCiTriggerHandlerController;

  beforeEach(mock.module(TIME_FORMATTERS, NETFLIX_CI_TRIGGER_HANDLER_COMPONENT));
  beforeEach(mock.inject((_$compile_: ICompileService,
                          $rootScope: IRootScopeService,
                          $httpBackend: IHttpBackendService,
                          _scmReader_: ScmReader) => {
    $compile = _$compile_;
    $scope = $rootScope;
    $http = $httpBackend;
    scmReader = _scmReader_;
  }));

  afterEach(() => {
    $http.verifyNoOutstandingExpectation();
    $http.verifyNoOutstandingRequest();
  });

  function compileAndSetController(command: any): void {
    $scope.command = command;
    elem = $compile('<netflix-ci-trigger-handler command="command"></netflix-ci-trigger-handler>')($scope);
    $scope.$digest();
    ctrl = elem.controller('netflixCiTriggerHandler');
  }

  function getCommand(project = 'project', slug = 'slug', branch = 'branch', type = 'git'): any {
    return {
      trigger: {project, slug, branch, type, enabled: true}
    };
  }

  function getRequestUrl(org: string, repository: string, scmItem: string, limit: number): string {
    return `${SCM_BASE_URL}/stash/orgs/${org}/repos/${repository}/${scmItem}?limit=${limit}`
  }

  it('should do nothing if the trigger source is not "stash"', () => {

    compileAndSetController(getCommand());
    expect(ctrl.command).toBe($scope.command);
    expect(ctrl.showOptions).toBe(false);

    const vs: IViewState = ctrl.viewState;
    expect(vs.buildSource).toBe('branch');
    expect(vs.branches.loaded).toBe(false);
    expect(vs.branches.loading).toBe(false);
    expect(vs.branches.loadError).toBe(false);
    expect(vs.commits.loaded).toBe(false);
    expect(vs.commits.loading).toBe(false);
    expect(vs.commits.loadError).toBe(false);
    expect(vs.tags.loaded).toBe(false);
    expect(vs.tags.loading).toBe(false);
    expect(vs.tags.loadError).toBe(false);

    expect(ctrl.branches).toBeUndefined();
    expect(ctrl.tags).toBeUndefined();
    expect(ctrl.commits).toBeUndefined();
    expect(elem.children().length).toBe(0);
  });

  describe('test the view state build source of "tag"', () => {

    let tags: ITag[];
    beforeEach(() => {
      tags = ['1', '2', '3'].map((item: string) => getNewTag(item));
    });

    afterEach(() => {
      elem = null;
      ctrl = null;
    });

    function getNewTag(id: string): ITag {
      return {id, displayId: `tagDisplayId${id}`, commitId: `tagCommitId${id}`};
    }

    it('should just set the hash to the first tag commit if the tags are already loaded', () => {

      compileAndSetController(getCommand());
      ctrl.viewState.buildSource = 'tag';
      ctrl.viewState.tags.loaded = true;
      ctrl.tags = tags;
      ctrl.buildSourceChanged();
      ctrl.showOptions = true;
      $scope.$digest();

      expect(ctrl.command.trigger.hash).toBe('tagCommitId1');
      expect(elem.find('label').last().text()).toBe('Tag');
    });

    it('should set the load error flag when the tags cannot be loaded', () => {

      const project = 'errorProject';
      const slug = 'errorSlug';
      $http.expectGET(getRequestUrl(project, slug, 'tags', 200)).respond(500);

      compileAndSetController(getCommand('errorProject', 'errorSlug'));
      ctrl.showOptions = true;
      ctrl.viewState.buildSource = 'tag';
      ctrl.buildSourceChanged();
      $http.flush();

      expect(ctrl.viewState.tags.loaded).toBe(false);
      expect(ctrl.viewState.tags.loadError).toBe(true);
      expect(elem.find('div').last().text()).toBe('Error loading tags!');
    });

    it('should load the tags, set the hash to the first commit, and set the loaded flag', () => {

      const project = 'project';
      const slug = 'slug';
      $http.expectGET(getRequestUrl(project, slug, 'tags', 200)).respond(200, {data: tags});
      compileAndSetController(getCommand(project, slug));
      ctrl.showOptions = true;
      ctrl.viewState.buildSource = 'tag';
      ctrl.buildSourceChanged();
      $http.flush();

      expect(ctrl.viewState.tags.loaded).toBe(true);
      expect(ctrl.command.trigger.hash).toBe('tagCommitId1');
      expect(elem.find('label').last().text()).toBe('Tag');
    });
  });

  describe('test the view state build source of "branch"', () => {

    let branches: IBranch[];
    beforeEach(() => {
      branches = ['1', '2', '3'].map((item: string) => getNewBranch(item));
    });

    afterEach(() => {
      elem = null;
      ctrl = null;
    });

    function getNewBranch(id: string): IBranch {
      return {id, displayId: `branchDisplayId${id}`, latestCommitId: `branchCommitId${id}`, 'default': true};
    }

    it('should just set the hash to the first branch latest commit id if the branches are already loaded', () => {

      compileAndSetController(getCommand());
      ctrl.viewState.branches.loaded = true;
      ctrl.branches = branches;
      ctrl.buildSourceChanged();
      ctrl.showOptions = true;
      $scope.$digest();

      expect(ctrl.command.trigger.hash).toBe('branchCommitId1');
      expect(elem.find('label').last().text()).toBe('Branch');
    });

    it('should set the load error flag when the branches cannot be loaded', () => {

      const project = 'errorProject';
      const slug = 'errorSlug';
      $http.expectGET(getRequestUrl(project, slug, 'branches', 100)).respond(500);

      compileAndSetController(getCommand('errorProject', 'errorSlug'));
      ctrl.showOptions = true;
      ctrl.buildSourceChanged();
      $http.flush();

      expect(ctrl.viewState.branches.loaded).toBe(false);
      expect(ctrl.viewState.branches.loadError).toBe(true);
      expect(elem.find('div').last().text()).toBe('Error loading branches!');
    });

    it('should load the branches by default, set the hash to the first branch latest commit id, and set the loaded flag', () => {

      const project = 'project';
      const slug = 'slug';
      $http.expectGET(getRequestUrl(project, slug, 'branches', 100)).respond(200, {data: branches});
      compileAndSetController(getCommand(project, slug));
      ctrl.showOptions = true;
      ctrl.buildSourceChanged();
      $http.flush();

      expect(ctrl.viewState.branches.loaded).toBe(true);
      expect(ctrl.command.trigger.hash).toBe('branchCommitId1');
      expect(elem.find('label').last().text()).toBe('Branch');
    });
  });

  describe('test the view state build source of "commit"', () => {

    let commits: ICommit[];
    beforeEach(() => {
      commits = [1, 2, 3].map((item: number) => getNewCommit(item));
    });

    afterEach(() => {
      elem = null;
      ctrl = null;
    });

    function getNewCommit(id: number): ICommit {
      return {
        id: `commitId${id}`,
        displayId: `commitDisplayId${id}`,
        message: `commitMessage${id}`,
        author: {
          username: `username${id}`,
          email: `username${id}@netflix.com`
        },
        authoredTs: Math.floor(Math.random() * 100)
      };
    }

    it('should just set the hash to the first commit id if the commits are already loaded', () => {

      compileAndSetController(getCommand());
      ctrl.viewState.buildSource = 'commit';
      ctrl.viewState.commits.loaded = true;
      ctrl.commits = commits;
      ctrl.buildSourceChanged();
      ctrl.showOptions = true;
      $scope.$digest();

      expect(ctrl.command.trigger.hash).toBe('commitId1');
      expect(elem.find('label').last().text()).toBe('Commit');
    });

    it('should set the load error flag when the commits cannot be loaded', () => {

      const project = 'errorProject';
      const slug = 'errorSlug';
      $http.expectGET(getRequestUrl(project, slug, 'commits', 250)).respond(500);

      compileAndSetController(getCommand('errorProject', 'errorSlug'));
      ctrl.showOptions = true;
      ctrl.viewState.buildSource = 'commit';
      ctrl.buildSourceChanged();
      $http.flush();

      expect(ctrl.viewState.commits.loaded).toBe(false);
      expect(ctrl.viewState.commits.loadError).toBe(true);
      expect(elem.find('div').last().text()).toBe('Error loading commits!');
    });

    it('should load the commits, set the hash to the first commit id, and set the loaded flag', () => {

      const project = 'project';
      const slug = 'slug';
      $http.expectGET(getRequestUrl(project, slug, 'commits', 250)).respond(200, {data: commits});
      compileAndSetController(getCommand(project, slug));
      ctrl.showOptions = true;
      ctrl.viewState.buildSource = 'commit';
      ctrl.buildSourceChanged();
      $http.flush();

      expect(ctrl.viewState.commits.loaded).toBe(true);
      expect(ctrl.command.trigger.hash).toBe('commitId1');
      expect(elem.find('label').last().text()).toBe('Commit');
    });
  });
});
