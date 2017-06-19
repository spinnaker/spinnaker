import {mock} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';
import {SECURITY_GROUP_TRANSFORMER_SERVICE, SecurityGroupTransformerService} from './securityGroupTransformer.service';
import {
  SECURITY_GROUP_READER, SecurityGroupReader,
  ISecurityGroupDetail
} from './securityGroupReader.service';
import {ISecurityGroup} from 'core/domain';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from 'core/application/applicationModel.builder';
import {Application} from 'core/application/application.model';

describe('Service: securityGroupReader', function () {

  let $q: ng.IQService,
    $http: ng.IHttpBackendService,
    $scope: ng.IRootScopeService,
    API: Api,
    applicationModelBuilder: ApplicationModelBuilder,
    reader: SecurityGroupReader;

  beforeEach(mock.module(APPLICATION_MODEL_BUILDER, SECURITY_GROUP_TRANSFORMER_SERVICE, SECURITY_GROUP_READER, API_SERVICE));
  beforeEach(
    mock.inject(function (_$q_: ng.IQService,
                          $httpBackend: ng.IHttpBackendService,
                          $rootScope: ng.IRootScopeService,
                          _API_: Api,
                          _applicationModelBuilder_: ApplicationModelBuilder,
                          _serviceDelegate_: any,
                          securityGroupTransformer: SecurityGroupTransformerService,
                          _securityGroupReader_: SecurityGroupReader) {
      reader = _securityGroupReader_;
      $http = $httpBackend;
      API = _API_;
      applicationModelBuilder = _applicationModelBuilder_;
      $q = _$q_;
      $scope = $rootScope.$new();

      spyOn(securityGroupTransformer, 'normalizeSecurityGroup')
        .and
        .callFake((securityGroup: ISecurityGroup) => {
          return $q.when(securityGroup);
        });
      spyOn(_serviceDelegate_, 'getDelegate').and.returnValue(
        {
          resolveIndexedSecurityGroup: (idx: any, container: ISecurityGroup, id: string) => {
            return idx[container.account][container.region][id];
          }
        }
      );
    })
  );

  it('attaches load balancer to security group usages', function () {
    let data: any[] = null;

    const application: Application = applicationModelBuilder.createApplication(
      'app',
      {
        key: 'securityGroups',
        data: [],
        ready: () => $q.when(null),
      },
      {
        key: 'serverGroups',
        data: [],
        ready: () => $q.when(null),
        loaded: true
      },
      {
        key: 'loadBalancers',
        data: [
          {
            name: 'my-elb',
            account: 'test',
            region: 'us-east-1',
            securityGroups: [
              'not-cached',
            ]
          }
        ],
        ready: () => $q.when(null),
        loaded: true
      });

    $http.expectGET(`${API.baseUrl}/securityGroups`)
      .respond(200, {
        test: {
            aws: {
              'us-east-1': [{name: 'not-cached'}]
            }
        }
      });
    reader.getApplicationSecurityGroups(application, null).then((results: any[]) => data = results);
    $http.flush();
    $scope.$digest();
    const group: ISecurityGroup = data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({name: application.getDataSource('loadBalancers').data[0].name});
  });

  it('adds security group names across accounts, falling back to the ID if none found', function () {
    let details: ISecurityGroupDetail = null;
    const application: Application = applicationModelBuilder.createApplication('app');
    application['securityGroupsIndex'] = {
      test: {'us-east-1': {'sg-2': {name: 'matched'}}},
      prod: {'us-east-1': {'sg-2': {name: 'matched-prod'}}}
    };

    $http.expectGET(`${API.baseUrl}/securityGroups/test/us-east-1/sg-123?provider=aws&vpcId=vpc-1`).respond(200, {
      inboundRules: [
        {securityGroup: {accountName: 'test', id: 'sg-345'}},
        {securityGroup: {accountName: 'test', id: 'sg-2'}},
        {securityGroup: {accountName: 'prod', id: 'sg-2'}},
      ],
      region: 'us-east-1',
    });

    reader.getSecurityGroupDetails(application, 'test', 'aws', 'us-east-1', 'vpc-1', 'sg-123').then(
      (result) => details = result);
    $http.flush();

    expect(details.securityGroupRules.length).toBe(3);
    expect(details.securityGroupRules[0].securityGroup.name).toBe('sg-345');
    expect(details.securityGroupRules[0].securityGroup.inferredName).toBe(true);
    expect(details.securityGroupRules[1].securityGroup.name).toBe('matched');
    expect(details.securityGroupRules[1].securityGroup.inferredName).toBeFalsy();
    expect(details.securityGroupRules[2].securityGroup.name).toBe('matched-prod');
    expect(details.securityGroupRules[2].securityGroup.inferredName).toBeFalsy();
  });

  it('should clear cache, then reload security groups and try again if a security group is not found', function () {
    let data: ISecurityGroup[] = null;
    const application: Application = applicationModelBuilder.createApplication(
      'app',
      {
        key: 'securityGroups',
      },
      {
        key: 'serverGroups',
        ready: () => $q.when(null),
        loaded: true
      },
      {
        securityGroupsIndex: {}
      },
      {
        key: 'loadBalancers',
        ready: () => $q.when(null),
        loaded: true
      });
    application.getDataSource('securityGroups').data = [];
    application.getDataSource('serverGroups').data = [];
    application.getDataSource('loadBalancers').data = [
      {
        name: 'my-elb',
        account: 'test',
        region: 'us-east-1',
        securityGroups: [
          'not-cached',
        ]
      }
    ];

    $http.expectGET(API.baseUrl + '/securityGroups').respond(200, {
      test: {
        aws: {
          'us-east-1': [
            {name: 'not-cached', id: 'not-cached-id', vpcId: null}
          ]
        }
      }
    });

    reader.getApplicationSecurityGroups(application, []).then(results => data = results);
    $http.flush();
    const group: ISecurityGroup = data[0];
    expect(group.name).toBe('not-cached');
    expect(group.usages.loadBalancers[0]).toEqual({name: application.getDataSource('loadBalancers').data[0].name});
  });
});


