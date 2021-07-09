import { mockHttpClient } from '../api/mock/jasmine';
import { mock } from 'angular';

import { Application } from '../application/application.model';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { InfrastructureCaches } from '../cache';
import { ISecurityGroup } from '../domain';
import {
  ISecurityGroupDetail,
  SECURITY_GROUP_READER,
  SecurityGroupReader,
  ISecurityGroupsByAccountSourceData,
} from './securityGroupReader.service';
import {
  SECURITY_GROUP_TRANSFORMER_SERVICE,
  SecurityGroupTransformerService,
} from './securityGroupTransformer.service';

describe('Service: securityGroupReader', function () {
  let $q: ng.IQService, $scope: ng.IRootScopeService, reader: SecurityGroupReader;

  beforeEach(mock.module(SECURITY_GROUP_TRANSFORMER_SERVICE, SECURITY_GROUP_READER));
  beforeEach(
    mock.inject(function (
      _$q_: ng.IQService,
      $rootScope: ng.IRootScopeService,
      _providerServiceDelegate_: any,
      securityGroupTransformer: SecurityGroupTransformerService,
      _securityGroupReader_: SecurityGroupReader,
    ) {
      reader = _securityGroupReader_;
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

  it('attaches load balancer to firewall usages', async function () {
    const http = mockHttpClient();
    let data: any[] = null;

    const application: Application = ApplicationModelBuilder.createApplicationForTests(
      'app',
      {
        key: 'securityGroups',
        loader: () => $q.resolve([]),
        onLoad: (_app, _data) => $q.resolve(_data),
        defaultData: [],
      },
      {
        key: 'serverGroups',
        loader: () => $q.resolve([]),
        onLoad: (_app, _data) => $q.resolve(_data),
        defaultData: [],
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
        defaultData: [],
      },
    );

    application.serverGroups.refresh();
    application.loadBalancers.refresh();
    $scope.$digest();

    http.expectGET(`/securityGroups`).respond(200, {
      test: {
        aws: {
          'us-east-1': [{ name: 'not-cached' }],
        },
      },
    });
    reader.getApplicationSecurityGroups(application, null).then((results: any[]) => (data = results));
    await http.flush();
    $scope.$digest();
    const group: ISecurityGroup = data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({ name: application.getDataSource('loadBalancers').data[0].name });
  });

  it('adds firewall names across accounts, falling back to the ID if none found', async function () {
    const http = mockHttpClient();
    let details: ISecurityGroupDetail = null;
    const application: Application = ApplicationModelBuilder.createApplicationForTests('app');
    application['securityGroupsIndex'] = {
      test: { 'us-east-1': { 'sg-2': { name: 'matched' } } },
      prod: { 'us-east-1': { 'sg-2': { name: 'matched-prod' } } },
    };

    http.expectGET(`/securityGroups/test/us-east-1/sg-123?provider=aws&vpcId=vpc-1`).respond(200, {
      inboundRules: [
        { securityGroup: { accountName: 'test', id: 'sg-345' } },
        { securityGroup: { accountName: 'test', id: 'sg-2' } },
        { securityGroup: { accountName: 'prod', id: 'sg-2' } },
      ],
      region: 'us-east-1',
    });

    reader
      .getSecurityGroupDetails(application, 'test', 'aws', 'us-east-1', 'vpc-1', 'sg-123')
      .then((result) => (details = result));
    await http.flush();

    expect(details.securityGroupRules.length).toBe(3);
    expect(details.securityGroupRules[0].securityGroup.name).toBe('sg-345');
    expect(details.securityGroupRules[0].securityGroup.inferredName).toBe(true);
    expect(details.securityGroupRules[1].securityGroup.name).toBe('matched');
    expect(details.securityGroupRules[1].securityGroup.inferredName).toBeFalsy();
    expect(details.securityGroupRules[2].securityGroup.name).toBe('matched-prod');
    expect(details.securityGroupRules[2].securityGroup.inferredName).toBeFalsy();
  });

  it('should clear cache, then reload firewalls and try again if a firewall is not found', async function () {
    const http = mockHttpClient();
    let data: ISecurityGroup[] = null;
    const application: Application = ApplicationModelBuilder.createApplicationForTests(
      'app',
      {
        key: 'securityGroups',
        defaultData: [],
      },
      {
        key: 'serverGroups',
        loader: () => $q.resolve([]),
        onLoad: (_app, _data) => $q.resolve(_data),
        defaultData: [],
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
        defaultData: [],
      },
    );

    application.getDataSource('securityGroups').refresh();
    application.getDataSource('serverGroups').refresh();
    application.getDataSource('loadBalancers').refresh();
    $scope.$digest();

    http.expectGET('/securityGroups').respond(200, {
      test: {
        aws: {
          'us-east-1': [{ name: 'not-cached', id: 'not-cached-id', vpcId: null }],
        },
      },
    });

    reader.getApplicationSecurityGroups(application, []).then((results) => (data = results));
    await http.flush();
    const group: ISecurityGroup = data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({ name: application.getDataSource('loadBalancers').data[0].name });
  });

  it('Should not fetch groups again while fetching', async () => {
    const http = mockHttpClient();
    const application: Application = ApplicationModelBuilder.createApplicationForTests('app', {
      key: 'securityGroups',
      loader: () => $q.resolve([]),
      onLoad: (_app, _data) => $q.resolve(_data),
      defaultData: [],
    });

    application.getDataSource('securityGroups').refresh();

    const groupName1 = 'testGroup1';
    http.expectGET('/securityGroups').respond(200, {
      [groupName1]: {
        aws: {
          'us-east-1': [{ name: 'hello', id: 'hello-id', vpcId: null }],
        },
      },
    });

    let data: ISecurityGroupsByAccountSourceData;
    reader.getAllSecurityGroups().then((results) => (data = results));
    reader.getAllSecurityGroups().then((results) => (data = results));
    await http.flush();
    expect(data[groupName1]).toBeDefined(`Group ${groupName1} is missing`);
    expect(http.receivedRequests.length).toBe(1, 'Should only fetch once');

    const groupName2 = 'testGroup2';
    http.expectGET('/securityGroups').respond(200, {
      [groupName2]: {
        aws: {
          'us-east-1': [{ name: 'hello', id: 'hello-id', vpcId: null }],
        },
      },
    });

    reader.getAllSecurityGroups().then((results) => (data = results));
    await http.flush();
    expect(http.receivedRequests.length).toBe(2, 'Should fetch again');
    expect(data[groupName1]).toBeUndefined(`Group ${groupName1} is defined`);
    expect(data[groupName2]).toBeDefined(`Group ${groupName2} is missing`);
  });
});
