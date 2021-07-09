import React from 'react';
import { mock, noop, IScope } from 'angular';
import { mount, shallow } from 'enzyme';

import {
  Application,
  ApplicationModelBuilder,
  ApplicationDataSource,
  IMoniker,
  IServerGroup,
  REACT_MODULE,
} from '@spinnaker/core';

import { AccountRegionClusterSelector, IAccountRegionClusterSelectorProps } from './AccountRegionClusterSelector';

describe('<AccountRegionClusterSelector />', () => {
  let $scope: IScope;
  let application: Application;

  function createServerGroup(account: string, cluster: string, name: string, region: string): IServerGroup {
    return {
      account,
      cloudProvider: 'cloud-provider',
      cluster,
      name,
      region,
      instances: [{ health: null, id: 'instance-id', launchTime: 0, name: 'instance-name', zone: 'GMT' }],
      instanceCounts: { up: 1, down: 0, starting: 0, succeeded: 1, failed: 0, unknown: 0, outOfService: 0 },
      moniker: { app: 'my-app', cluster, detail: 'my-detail', stack: 'my-stack', sequence: 1 },
    } as IServerGroup;
  }

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(
    mock.inject(($rootScope: IScope) => {
      $scope = $rootScope.$new();
      application = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'serverGroups',
        loaded: true,
        data: [
          createServerGroup('account-name-one', 'app-stack-detailOne', 'app', 'region-one'),
          createServerGroup('account-name-two', 'app-stack-detailTwo', 'app', 'region-two'),
          createServerGroup('account-name-one', 'app-stack-detailOne', 'app', 'region-three'),
          createServerGroup('account-name-one', 'app-stack-detailThree', 'app', 'region-one'),
          createServerGroup('account-name-one', 'app-stack-detailFour', 'app', 'region-three'),
          createServerGroup('account-name-one', 'app-stack-detailFive', 'app', 'region-two'),
        ],
        defaultData: [] as IServerGroup[],
      } as ApplicationDataSource<IServerGroup[]>);
    }),
  );

  it('initializes properly with provided component', () => {
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      onComponentUpdate: noop,
      component: {
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    const component = shallow<AccountRegionClusterSelector>(
      <AccountRegionClusterSelector {...accountRegionClusterProps} />,
    );
    $scope.$digest();

    expect(component.state().availableRegions.length).toBe(3, 'number of available regions does not match');
    expect(component.state().availableRegions).toContain('region-one');
    expect(component.state().availableRegions).toContain('region-two');
    expect(component.state().availableRegions).toContain('region-three');
    expect(component.state().clusters.length).toBe(2, 'number of clusters does not match');
    expect(component.state().clusters).toContain('app-stack-detailOne');
    expect(component.state().clusters).toContain('app-stack-detailThree');
    expect(component.state().clusterField).toBe('cluster');
    expect(component.state().componentName).toBe('');
  });

  it('retrieves the correct list of regions when account is changed', () => {
    let credentials = '';
    let region = 'SHOULD-CHANGE';
    let regions = ['SHOULD-CHANGE'];
    let cluster = 'SHOULD-CHANGE';
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      onComponentUpdate: (value: any) => {
        credentials = value.credentials;
        region = value.region;
        regions = value.regions;
        cluster = value.cluster;
      },
      component: {
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    const component = mount<AccountRegionClusterSelector>(
      <AccountRegionClusterSelector {...accountRegionClusterProps} />,
    );
    $scope.$digest();

    expect(component.state().availableRegions.length).toBe(3, 'number of available regions does not match');
    expect(component.state().availableRegions).toContain('region-one');
    expect(component.state().availableRegions).toContain('region-two');
    expect(component.state().availableRegions).toContain('region-three');
    expect(component.state().clusters.length).toBe(2, 'number of clusters does not match');
    expect(component.state().clusters).toContain('app-stack-detailOne');
    expect(component.state().clusters).toContain('app-stack-detailThree');

    const accountSelectComponent = component.find('Select[name="credentials"] .Select-control input');
    accountSelectComponent.simulate('mouseDown');
    accountSelectComponent.simulate('change', { target: { value: 'account-name-two' } });
    accountSelectComponent.simulate('keyDown', { keyCode: 9, key: 'Tab' });
    $scope.$digest();

    expect(component.state().availableRegions.length).toBe(1, 'number of available regions does not match');
    expect(component.state().availableRegions).toContain('region-two');
    expect(component.state().clusters.length).toBe(0, 'number of clusters does not match');
    expect(region).toEqual('', 'selected region is not cleared');
    expect(regions.length).toBe(0, 'selected regions list is not cleared');
    expect(credentials).toContain('account-name-two');
    expect(cluster).toBeUndefined('selected cluster is not cleared');
  });

  it('retrieves the correct list of clusters when the selector is multi-region and the region is changed', () => {
    let regions: string[] = [];
    let cluster = 'SHOULD-CHANGE';
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      clusterField: 'newCluster',
      onComponentUpdate: (value: any) => {
        regions = value.regions;
        cluster = value.newCluster;
      },
      component: {
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    const component = mount<AccountRegionClusterSelector>(
      <AccountRegionClusterSelector {...accountRegionClusterProps} />,
    );
    $scope.$digest();

    expect(component.state().availableRegions.length).toBe(3, 'number of available regions does not match');
    expect(component.state().availableRegions).toContain('region-one');
    expect(component.state().availableRegions).toContain('region-two');
    expect(component.state().availableRegions).toContain('region-three');
    expect(component.state().clusters.length).toBe(2, 'number of clusters does not match');
    expect(component.state().clusters).toContain('app-stack-detailOne');
    expect(component.state().clusters).toContain('app-stack-detailThree');

    const accountSelectComponent = component.find('Select[name="regions"] .Select-control input');
    accountSelectComponent.simulate('mouseDown');
    accountSelectComponent.simulate('change', { target: { value: 'region-three' } });
    accountSelectComponent.simulate('keyDown', { keyCode: 9, key: 'Tab' });
    $scope.$digest();

    expect(component.state().clusters.length).toBe(3, 'number of clusters does not match');
    expect(component.state().clusters).toContain('app-stack-detailOne');
    expect(component.state().clusters).toContain('app-stack-detailThree');
    expect(component.state().clusters).toContain('app-stack-detailFour');
    expect(cluster).toBeUndefined('selected cluster is not cleared');
    expect(regions.length).toBe(2);
    expect(regions).toContain('region-one');
    expect(regions).toContain('region-three');
  });

  it('retrieves the correct list of clusters on startup and the selector is single-region', () => {
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      onComponentUpdate: (_value: any) => {},
      component: {
        cluster: 'app-stack-detailOne',
        credentials: 'account-name-one',
        region: 'region-one',
      },
      isSingleRegion: true,
    };

    const component = mount<AccountRegionClusterSelector>(
      <AccountRegionClusterSelector {...accountRegionClusterProps} />,
    );
    $scope.$digest();

    expect(component.state().availableRegions.length).toBe(3, 'number of available regions does not match');
    expect(component.state().availableRegions).toContain('region-one');
    expect(component.state().availableRegions).toContain('region-two');
    expect(component.state().availableRegions).toContain('region-three');
    expect(component.state().clusters.length).toBe(2, 'number of clusters does not match');
    expect(component.state().clusters).toContain('app-stack-detailOne');
    expect(component.state().clusters).toContain('app-stack-detailThree');
  });

  it('the cluster value is updated in the component when cluster is changed', () => {
    let cluster = '';
    let moniker: IMoniker = { app: '' };
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      clusterField: 'newCluster',
      onComponentUpdate: (value: any) => {
        cluster = value.newCluster;
        moniker = value.moniker;
      },
      component: {
        cluster: 'app-stack-detailOne',
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    const expectedMoniker = {
      app: 'my-app',
      cluster: 'app-stack-detailThree',
      detail: 'my-detail',
      stack: 'my-stack',
      sequence: null,
    } as IMoniker;

    const component = mount<AccountRegionClusterSelector>(
      <AccountRegionClusterSelector {...accountRegionClusterProps} />,
    );
    $scope.$digest();

    expect(component.state().availableRegions.length).toBe(3, 'number of available regions does not match');
    expect(component.state().availableRegions).toContain('region-one');
    expect(component.state().availableRegions).toContain('region-two');
    expect(component.state().availableRegions).toContain('region-three');
    expect(component.state().clusters.length).toBe(2, 'number of clusters does not match');
    expect(component.state().clusters).toContain('app-stack-detailOne');
    expect(component.state().clusters).toContain('app-stack-detailThree');

    const clusterSelectComponent = component.find('Select[name="newCluster"] .Select-control input');
    clusterSelectComponent.simulate('mouseDown');
    clusterSelectComponent.simulate('change', { target: { value: 'app-stack-detailThree' } });
    clusterSelectComponent.simulate('keyDown', { keyCode: 9, key: 'Tab' });
    $scope.$digest();

    expect(cluster).toBe('app-stack-detailThree');
    expect(moniker).toEqual(expectedMoniker);
  });

  it('the cluster value is updated in the component when cluster is changed to freeform value', () => {
    let cluster = '';
    let moniker: IMoniker = { app: '' };
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-two',
          name: 'account-name-two',
          requiredGroupMembership: [],
          type: 'account-type',
        },
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      clusterField: 'newCluster',
      onComponentUpdate: (value: any) => {
        cluster = value.newCluster;
        moniker = value.moniker;
      },
      component: {
        cluster: 'app-stack-detailOne',
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    const component = mount<AccountRegionClusterSelector>(
      <AccountRegionClusterSelector {...accountRegionClusterProps} />,
    );
    $scope.$digest();

    const clusterSelectComponent = component.find('Select[name="newCluster"] .Select-control input');
    clusterSelectComponent.simulate('mouseDown');
    clusterSelectComponent.simulate('change', { target: { value: 'app-stack-freeform' } });
    clusterSelectComponent.simulate('keyDown', { keyCode: 9, key: 'Tab' });
    $scope.$digest();

    expect(cluster).toBe('app-stack-freeform');
    expect(moniker).toBeUndefined();
    expect(component.state().clusters).toContain('app-stack-freeform');
  });

  it('initialize with form names', () => {
    const accountRegionClusterProps: IAccountRegionClusterSelectorProps = {
      accounts: [
        {
          accountId: 'account-id-one',
          name: 'account-name-one',
          requiredGroupMembership: [],
          type: 'account-type',
        },
      ],
      application,
      cloudProvider: 'cloud-provider',
      onComponentUpdate: noop,
      componentName: 'form',
      component: {
        credentials: 'account-name-one',
        regions: ['region-one'],
      },
    };

    const component = shallow(<AccountRegionClusterSelector {...accountRegionClusterProps} />);
    $scope.$digest();

    expect(component.find('Select[name="form.credentials"]').length).toBe(1, 'select for account not found');
    expect(component.find('Select[name="form.regions"]').length).toBe(1, 'select for regions not found');
    expect(component.find('StageConfigField [name="form.cluster"]').length).toBe(1, 'select for cluster not found');
  });
});
