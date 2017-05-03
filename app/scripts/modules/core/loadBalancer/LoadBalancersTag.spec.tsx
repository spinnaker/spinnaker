import { mock, IQService, IScope } from 'angular';
import * as React from 'react';
import { ReactWrapper, mount } from 'enzyme';

import { Application } from '../application/application.model';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from 'core/application/applicationModel.builder';
import { ILoadBalancersTagProps, LoadBalancersTag } from './LoadBalancersTag';
import { ServerGroup } from 'core/domain';

describe('<LoadBalancersTag />', () => {
  const lb1 = { name: 'lb1', account: 'prod', region: 'us-east-1', vpcId: 'vpc-1' },
        lb2 = { name: 'lb2', account: 'prod', region: 'us-east-1' };

  let $q: IQService,
      $scope: IScope,
      application: Application,
      component: ReactWrapper<ILoadBalancersTagProps, any>;

  beforeEach(
    mock.module(
      APPLICATION_MODEL_BUILDER
    )
  );

  beforeEach(mock.inject((_$q_: IQService, $rootScope: IScope, applicationModelBuilder: ApplicationModelBuilder) => {
    $q = _$q_;
    $scope = $rootScope.$new();
    application = applicationModelBuilder.createApplication({
      key: 'loadBalancers',
      loader: () => $q.when(null),
      onLoad: () => $q.when(null),
      loaded: true,
    });
  }));

  it('extracts single load balancer from data', () => {
    const serverGroup = {
      account: 'prod',
      region: 'us-east-1',
      type: 'aws',
      loadBalancers: ['lb1'],
      instances: [],
    } as ServerGroup;

    application.getDataSource('loadBalancers').data = [ lb1, lb2 ];

    const props: ILoadBalancersTagProps = { application, serverGroup };
    component = mount(<LoadBalancersTag {...props}/>);

    expect(component.find('span.btn-load-balancer').length).toBe(1);
  });

  it('extracts two load balancers from data', () => {
    const serverGroup = {
      account: 'prod',
      region: 'us-east-1',
      type: 'aws',
      loadBalancers: ['lb1', 'lb2'],
      instances: [],
    } as ServerGroup;

    application.getDataSource('loadBalancers').data = [ lb1, lb2 ];

    const props: ILoadBalancersTagProps = { application, serverGroup };
    component = mount(<LoadBalancersTag {...props}/>);

    // Make sure the application dataSource promises resolve
    $scope.$digest();

    expect(component.find('.btn-multiple-load-balancers').length).toBe(1);
    component.find('.btn-multiple-load-balancers').simulate('click');
    const menuChildren: ReactWrapper<any, any> = component.find('div.menu-load-balancers').children();
    expect(menuChildren.length).toBe(3);
    expect(menuChildren.at(0).text().trim()).toBe('Load Balancers');
    expect(menuChildren.at(1).find('.name').text().trim()).toBe('lb1');
    expect(menuChildren.at(2).find('.name').text().trim()).toBe('lb2');
  });

  it('renders instance counts', () => {
    const serverGroup = {
      account: 'prod',
      region: 'us-east-1',
      type: 'aws',
      loadBalancers: ['lb1', 'lb2'],
      instances: [
      {
        id: 'not-in-lb',
        health: [
          { type: 'Discovery' },
        ]
      },
      {
        id: 'in-one-lb',
        health: [
          {
            type: 'LoadBalancer',
            loadBalancers: [
              { name: 'lb1', healthState: 'Up' },
              { name: 'some-other-lb', healthState: 'Down' }
            ]
          }
        ]
      },
      {
        id: 'in-two-lbs',
        health: [
          {
            type: 'LoadBalancer',
            loadBalancers: [
              { name: 'lb1', healthState: 'Up' },
              { name: 'lb2', healthState: 'Down' }
            ]
          }
        ]
      }
    ]} as ServerGroup;

    application.getDataSource('loadBalancers').data = [ lb1, lb2 ];

    const props: ILoadBalancersTagProps = { application, serverGroup };
    component = mount(<LoadBalancersTag {...props}/>);

    // Make sure the application dataSource promises resolve
    $scope.$digest();

    const instance = component.instance() as LoadBalancersTag;
    const builtLB1 = instance.buildLoadBalancer(lb1);
    expect(builtLB1.instanceCounts).toEqual({ up: 2, down: 0, succeeded: 0, failed: 0, outOfService: 0, unknown: 0, starting: 0 });
    const builtLB2 = instance.buildLoadBalancer(lb2);
    expect(builtLB2.instanceCounts).toEqual({ up: 0, down: 1, succeeded: 0, failed: 0, outOfService: 0, unknown: 0, starting: 0 });
  });
});
