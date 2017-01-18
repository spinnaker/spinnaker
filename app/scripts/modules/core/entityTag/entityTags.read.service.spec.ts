import {mock} from 'angular';

import {EntityTagsReader, ENTITY_TAGS_READ_SERVICE} from './entityTags.read.service';
import IProvideService = angular.auto.IProvideService;

describe('entityTags reader', () => {

  let $http: ng.IHttpBackendService;
  let $q: ng.IQService;
  let $scope: ng.IScope;
  let $exceptionHandler: ng.IExceptionHandlerService;
  let service: EntityTagsReader;
  let settings: any;

  beforeEach(mock.module(ENTITY_TAGS_READ_SERVICE));

  beforeEach(mock.module(($exceptionHandlerProvider: ng.IExceptionHandlerProvider, $provide: IProvideService) => {
    $exceptionHandlerProvider.mode('log');
    return $provide.constant('settings', {
      gateUrl: 'http://gate',
      entityTags: {
        maxUrlLength: 55,
      },
    });
  }));

  beforeEach(
    mock.inject(($httpBackend: ng.IHttpBackendService,
                 _$q_: ng.IQService,
                 $rootScope: ng.IRootScopeService,
                 _$exceptionHandler_: ng.IExceptionHandlerService,
                 entityTagsReader: EntityTagsReader,
                 _settings_: any) => {
      $http = $httpBackend;
      $q = _$q_;
      $scope = $rootScope.$new();
      service = entityTagsReader;
      settings = _settings_;
      $exceptionHandler = _$exceptionHandler_;
    }));

  it('returns an empty list instead of failing if tags cannot be loaded', () => {
    $http.expectGET(`${settings.gateUrl}/tags?entityId=a,b&entityType=servergroups`).respond(400, 'bad request');
    let result: any = null;
    service.getAllEntityTags('serverGroups', ['a', 'b']).then(r => result = r);
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
    $scope.$digest();
    expect(result).toEqual([]);
    expect(($exceptionHandler as any)['errors'].length).toBe(0);
  });
});
