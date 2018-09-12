import { mock } from 'angular';

import { API } from 'core/api/ApiService';
import { Application } from 'core/application/application.model';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';
import { InfrastructureCaches } from 'core/cache';
import { ISecurityGroup } from 'core/domain';
import { ISecurityGroupDetail, SECURITY_GROUP_READER, SecurityGroupReader } from './securityGroupReader.service';
import {
  SECURITY_GROUP_TRANSFORMER_SERVICE,
  SecurityGroupTransformerService,
} from './securityGroupTransformer.service';

describe('Service: securityGroupReader', function() {
  let $q: ng.IQService,
    $http: ng.IHttpBackendService,
    $scope: ng.IRootScopeService,
    applicationModelBuilder: ApplicationModelBuilder,
    reader: SecurityGroupReader;

  beforeEach(mock.module(APPLICATION_MODEL_BUILDER, SECURITY_GROUP_TRANSFORMER_SERVICE, SECURITY_GROUP_READER));
  beforeEach(
    mock.inject(function(
      _$q_: ng.IQService,
      $httpBackend: ng.IHttpBackendService,
      $rootScope: ng.IRootScopeService,
      _applicationModelBuilder_: ApplicationModelBuilder,
      _providerServiceDelegate_: any,
      securityGroupTransformer: SecurityGroupTransformerService,
      _securityGroupReader_: SecurityGroupReader,
    ) {
      reader = _securityGroupReader_;
      $http = $httpBackend;
      applicationModelBuilder = _applicationModelBuilder_;
      $q = _$q_;
      $scope = $rootScope.$new();

      const cacheStub: any = {
        get: () => null as any,
        put: () => {},
      };
      spyOn(InfrastructureCaches, 'get').and.returnValue(cacheStub);

      spyOn(securityGroupTransformer, 'normalizeSecurityGroup').and.callFake((securityGroup: ISecurityGroup) => {
        return $q.when(securityGroup);
      });
      spyOn(_providerServiceDelegate_, 'getDelegate').and.returnValue({
        resolveIndexedSecurityGroup: (idx: any, container: ISecurityGroup, id: string) => {
          return idx[container.account][container.region][id];
        },
      });
    }),
  );

  it('attaches load balancer to firewall usages', function() {
    let data: any[] = null;

    const application: Application = applicationModelBuilder.createApplication(
      'app',
      {
        key: 'securityGroups',
        loader: () => $q.resolve([]),
        onLoad: (_app, _data) => $q.resolve(_data),
      },
      {
        key: 'serverGroups',
        loader: () => $q.resolve([]),
        onLoad: (_app, _data) => $q.resolve(_data),
      },
      {
        key: 'loadBalancers',
        loader: () =>
          $q.resolve([
            {
              name: 'my-elb',
              account: 'test',
              region: 'us-east-1',
              securityGroups: ['not-cached'],
            },
          ]),
        onLoad: (_app, _data) => $q.resolve(_data),
      },
    );

    application.serverGroups.refresh();
    application.loadBalancers.refresh();
    $scope.$digest();

    $http.expectGET(`${API.baseUrl}/securityGroups`).respond(200, {
      test: {
        aws: {
          'us-east-1': [{ name: 'not-cached' }],
        },
      },
    });
    reader.getApplicationSecurityGroups(application, null).then((results: any[]) => (data = results));
    $http.flush();
    $scope.$digest();
    const group: ISecurityGroup = data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({ name: application.getDataSource('loadBalancers').data[0].name });
  });

  it('adds firewall names across accounts, falling back to the ID if none found', function() {
    let details: ISecurityGroupDetail = null;
    const application: Application = applicationModelBuilder.createApplication('app');
    application['securityGroupsIndex'] = {
      test: { 'us-east-1': { 'sg-2': { name: 'matched' } } },
      prod: { 'us-east-1': { 'sg-2': { name: 'matched-prod' } } },
    };

    $http.expectGET(`${API.baseUrl}/securityGroups/test/us-east-1/sg-123?provider=aws&vpcId=vpc-1`).respond(200, {
      inboundRules: [
        { securityGroup: { accountName: 'test', id: 'sg-345' } },
        { securityGroup: { accountName: 'test', id: 'sg-2' } },
        { securityGroup: { accountName: 'prod', id: 'sg-2' } },
      ],
      region: 'us-east-1',
    });

    reader
      .getSecurityGroupDetails(application, 'test', 'aws', 'us-east-1', 'vpc-1', 'sg-123')
      .then(result => (details = result));
    $http.flush();

    expect(details.securityGroupRules.length).toBe(3);
    expect(details.securityGroupRules[0].securityGroup.name).toBe('sg-345');
    expect(details.securityGroupRules[0].securityGroup.inferredName).toBe(true);
    expect(details.securityGroupRules[1].securityGroup.name).toBe('matched');
    expect(details.securityGroupRules[1].securityGroup.inferredName).toBeFalsy();
    expect(details.securityGroupRules[2].securityGroup.name).toBe('matched-prod');
    expect(details.securityGroupRules[2].securityGroup.inferredName).toBeFalsy();
  });

  it('should clear cache, then reload firewalls and try again if a firewall is not found', function() {
    let data: ISecurityGroup[] = null;
    const application: Application = applicationModelBuilder.createApplication(
      'app',
      {
        key: 'securityGroups',
      },
      {
        key: 'serverGroups',
        loader: () => $q.resolve([]),
        onLoad: (_app, _data) => $q.resolve(_data),
      },
      {
        key: 'loadBalancers',
        loader: () =>
          $q.resolve([
            {
              name: 'my-elb',
              account: 'test',
              region: 'us-east-1',
              securityGroups: ['not-cached'],
            },
          ]),
        onLoad: (_app, _data) => $q.resolve(_data),
      },
    );

    application.getDataSource('securityGroups').refresh();
    application.getDataSource('serverGroups').refresh();
    application.getDataSource('loadBalancers').refresh();
    $scope.$digest();

    $http.expectGET(API.baseUrl + '/securityGroups').respond(200, {
      test: {
        aws: {
          'us-east-1': [{ name: 'not-cached', id: 'not-cached-id', vpcId: null }],
        },
      },
    });

    reader.getApplicationSecurityGroups(application, []).then(results => (data = results));
    $http.flush();
    const group: ISecurityGroup = data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({ name: application.getDataSource('loadBalancers').data[0].name });
  });
});
