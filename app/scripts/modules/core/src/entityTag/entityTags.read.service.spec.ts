import { IHttpBackendService, IQService, ITimeoutService, mock } from 'angular';

import {EntityTagsReader, ENTITY_TAGS_READ_SERVICE} from './entityTags.read.service';
import {SETTINGS} from 'core/config/settings';

describe('entityTags reader', () => {

  let $http: IHttpBackendService;
  let $q: IQService;
  let $timeout: ITimeoutService;
  let service: EntityTagsReader;

  beforeEach(function () {
    // Why do we have to clear the settings file? What setting is causing retries to not work?
    Object.keys(SETTINGS).forEach(key => {
      SETTINGS[key] = undefined;
    });
    SETTINGS.gateUrl = 'http://gate';
    SETTINGS.entityTags = { maxUrlLength: 55 };
  });

  beforeEach(mock.module(ENTITY_TAGS_READ_SERVICE));

  beforeEach(
    mock.inject(($httpBackend: IHttpBackendService,
                 _$q_: IQService,
                 entityTagsReader: EntityTagsReader,
                 _$timeout_: ITimeoutService) => {
      $http = $httpBackend;
      $q = _$q_;
      service = entityTagsReader;
      $timeout = _$timeout_;
    }));

  afterEach(SETTINGS.resetToOriginal);

  it('returns an empty list instead of failing if tags cannot be loaded', () => {
    $http.whenGET(`${SETTINGS.gateUrl}/tags?entityId=a,b&entityType=servergroups`).respond(400, 'bad request');
    let result: any = null;
    service.getAllEntityTags('serverGroups', ['a', 'b']).then(r => result = r);
    $http.flush();
    $timeout.flush();
    $http.flush();
    expect(result).toEqual([]);
  });

  it('collates entries into groups when there are too many', () => {
    $http.expectGET(`http://gate/tags?entityId=a,b&entityType=servergroups`).respond(200, []);
    $http.expectGET(`http://gate/tags?entityId=c,d&entityType=servergroups`).respond(200, []);

    let result: any = null;
    service.getAllEntityTags('serverGroups', ['a', 'b', 'c', 'd']).then(r => result = r);
    $http.flush();
    $timeout.flush();
    expect(result).toEqual([]);
  });

  it('retries server group fetch once on exceptions', () => {
    $http.expectGET(`${SETTINGS.gateUrl}/tags?entityId=a,b&entityType=servergroups`).respond(400, 'bad request');
    let result: any = null;
    service.getAllEntityTags('serverGroups', ['a', 'b']).then(r => result = r);
    $http.flush();
    $http.expectGET(`${SETTINGS.gateUrl}/tags?entityId=a,b&entityType=servergroups`).respond(200, []);
    $timeout.flush();
    $http.flush();
    expect(result).toEqual([]);
  });
});
