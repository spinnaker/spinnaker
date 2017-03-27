import {mock} from 'angular';

import {EntityTagsReader, ENTITY_TAGS_READ_SERVICE} from './entityTags.read.service';
import {SETTINGS} from 'core/config/settings';

describe('entityTags reader', () => {

  let $http: ng.IHttpBackendService;
  let $q: ng.IQService;
  let $timeout: ng.ITimeoutService;
  let $exceptionHandler: ng.IExceptionHandlerService;
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

  beforeEach(mock.module(($exceptionHandlerProvider: ng.IExceptionHandlerProvider) => {
    $exceptionHandlerProvider.mode('log');
  }));

  beforeEach(
    mock.inject(($httpBackend: ng.IHttpBackendService,
                 _$q_: ng.IQService,
                 _$exceptionHandler_: ng.IExceptionHandlerService,
                 entityTagsReader: EntityTagsReader,
                 _$timeout_: ng.ITimeoutService) => {
      $http = $httpBackend;
      $q = _$q_;
      service = entityTagsReader;
      $exceptionHandler = _$exceptionHandler_;
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
    expect(($exceptionHandler as any)['errors'].length).toBe(1);
  });

  it('collates entries into groups when there are too many', () => {
    $http.expectGET(`http://gate/tags?entityId=a,b&entityType=servergroups`).respond(200, []);
    $http.expectGET(`http://gate/tags?entityId=c,d&entityType=servergroups`).respond(200, []);

    let result: any = null;
    service.getAllEntityTags('serverGroups', ['a', 'b', 'c', 'd']).then(r => result = r);
    $http.flush();
    $timeout.flush();
    expect(result).toEqual([]);
    expect(($exceptionHandler as any)['errors'].length).toBe(0);
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
    expect(($exceptionHandler as any)['errors'].length).toBe(0);
  });
});
