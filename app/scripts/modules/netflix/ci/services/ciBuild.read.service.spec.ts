import * as moment from 'moment';
import {IHttpBackendService, mock} from 'angular';

import {SETTINGS} from 'core/config/settings';
import {Api, API_SERVICE} from 'core/api/api.service';
import {
  CI_BUILD_READ_SERVICE, CiBuildReader, ICiBuild
} from 'netflix/ci/services/ciBuild.read.service';
import {CiFilterModel} from 'netflix/ci/ciFilter.model';

describe('CiBuildReader', () => {

  const CI_BUILD_URL = `${SETTINGS.gateUrl}/ci/builds`;
  let $http: IHttpBackendService;
  let API: Api;
  let ciBuildReader: CiBuildReader;

  beforeEach(mock.module(API_SERVICE, CI_BUILD_READ_SERVICE));
  beforeEach(mock.inject((_$httpBackend_: IHttpBackendService,
                          _API_: Api,
                          _ciBuildReader_: CiBuildReader) => {
    $http = _$httpBackend_;
    API = _API_;
    ciBuildReader = _ciBuildReader_;
  }));

  beforeEach(() => {
    CiFilterModel.searchFilter = 'none';
  });

  afterEach(() => {
    CiFilterModel.searchFilter = null;
    $http.verifyNoOutstandingExpectation();
    $http.verifyNoOutstandingRequest();
  });

  function getBuild(buildId = 'buildId', completionStatus = 'COMPLETE'): ICiBuild {
    return {
      buildNumber: Math.floor(Math.random() * 100),
      change: null,
      completedAt: moment.now(),
      completionStatus: completionStatus,
      id: buildId,
      repositoryId: 'repoId',
      startedAt: moment.now() - 10000
    };
  }

  it('should retrieve a list of transformed builds', () => {
    const repoType = 'r_rt';
    const projectKey = 'r_pk';
    const repoSlug = 'r_rs';
    $http.expectGET(`${CI_BUILD_URL}?repoType=${repoType}&projectKey=${projectKey}&repoSlug=${repoSlug}&filter=${CiFilterModel.searchFilter}`)
      .respond(200, {data: [getBuild(), getBuild('buildId', 'INCOMPLETE')]});

    let result: ICiBuild[] = null;
    ciBuildReader.getBuilds(repoType, projectKey, repoSlug).then((builds: ICiBuild[]) => result = builds);
    $http.flush();
    expect(result.length).toBe(2);
    result.forEach((build: ICiBuild) => {
      expect(build.startTime).toBe(build.startedAt);
      expect(build.endTime).toBe(build.completedAt);
      expect(build.isRunning).toBe(build.completionStatus === 'INCOMPLETE');
      expect(build.runningTimeInMs).toBeDefined();
    })
  });

  it('should retrieve a list of running builds', () => {
    const repoType = 'r_rt';
    const projectKey = 'r_pk';
    const repoSlug = 'r_rs';
    $http.expectGET(`${CI_BUILD_URL}?repoType=${repoType}&projectKey=${projectKey}&repoSlug=${repoSlug}&completionStatus=INCOMPLETE`)
      .respond(200, {data: [getBuild(), getBuild('buildId', 'INCOMPLETE')]});

    let result: ICiBuild[] = null;
    ciBuildReader.getRunningBuilds(repoType, projectKey, repoSlug).then((builds: ICiBuild[]) => result = builds);
    $http.flush();
    expect(result.length).toBe(2);
    result.forEach((build: ICiBuild) => {
      expect(build.startTime).toBe(build.startedAt);
      expect(build.endTime).toBe(build.completedAt);
      expect(build.isRunning).toBe(build.completionStatus === 'INCOMPLETE');
      expect(build.runningTimeInMs).toBeDefined();
    })
  });

  it('should retrieve the transformed build details', () => {
    const buildId = 'bd_bid';
    $http.expectGET(`${CI_BUILD_URL}/${buildId}`)
      .respond(200, getBuild());

    let build: ICiBuild = null;
    ciBuildReader.getBuildDetails(buildId).then((b: ICiBuild) => build = b);
    $http.flush();
    expect(build.startTime).toBe(build.startedAt);
    expect(build.endTime).toBe(build.completedAt);
    expect(build.isRunning).toBe(build.completionStatus === 'INCOMPLETE');
    expect(build.runningTimeInMs).toBeDefined();
  });

  it('should set the default start to -1 when not specified for build output requests', () => {
    const buildId = 'bo_bid';
    $http.expectGET(`${CI_BUILD_URL}/${buildId}/output?start=-1&limit=${CiBuildReader.MAX_LINES}`)
      .respond(200, {});

    ciBuildReader.getBuildOutput(buildId);
    $http.flush();
  });

  it('should use the specified start parameter value for build output requests', () => {
    const buildId = 'bo_bid';
    const start = 20;

    $http.expectGET(`${CI_BUILD_URL}/${buildId}/output?start=${start}&limit=${CiBuildReader.MAX_LINES}`)
      .respond(200, {});
    ciBuildReader.getBuildOutput(buildId, start);
    $http.flush();
  });

  it('should return the build config', () => {
    const buildId = 'bc_bid';
    $http.expectGET(`${CI_BUILD_URL}/${buildId}/config`).respond(200, {});

    ciBuildReader.getBuildConfig(buildId);
    $http.flush();
  });

  it('should return the raw log link', () => {
    const buildId = 'rll_bid';
    const actual = ciBuildReader.getBuildRawLogLink(buildId);

    const expected = `${SETTINGS.gateUrl}/ci/builds/${buildId}/rawOutput`;
    expect(actual).toBe(expected);
  });
});
