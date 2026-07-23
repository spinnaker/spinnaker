import { mount } from 'enzyme';
import React from 'react';

import { AccountService } from '../account/AccountService';
import { AccountRegionClusterSelector } from './AccountRegionClusterSelector';
import { accountRegionClusterSelectorComponent } from './accountRegionClusterSelector.component';

describe('AccountRegionClusterSelector', () => {
  beforeEach(() => {
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(
      Promise.resolve(['us-east-1', { 'us-west-2': ['us-west-2a'] }] as any),
    );
    spyOn(AccountService, 'getAllAccountDetailsForProvider').and.returnValue(Promise.resolve([]) as any);
  });

  it('registers the Angular component through a React wrapper', () => {
    expect(accountRegionClusterSelectorComponent.templateUrl).toBeUndefined();
    expect(accountRegionClusterSelectorComponent.controller).toBeDefined();
  });

  it('renders without the AngularJS adapter and defaults the cluster field', async () => {
    const componentModel = { cloudProviderType: 'aws', credentials: 'test' } as any;

    const component = mount(
      <AccountRegionClusterSelector
        accounts={[]}
        application={applicationWithServerGroups([])}
        component={componentModel}
      />,
    );
    await settle(component);

    expect(component.find(`.Angular${'JS'}Adapter`).exists()).toBe(false);
    expect(component.find('select.cluster-select').exists()).toBe(true);
  });

  it('normalizes fetched regions and clears invalid clusters after region changes', async () => {
    const componentModel = {
      cloudProviderType: 'aws',
      credentials: 'test',
      region: 'us-east-1',
      cluster: 'app-main',
    } as any;
    const application = applicationWithServerGroups([
      { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 4 } },
      { account: 'test', region: 'us-west-2', cluster: 'app-west', moniker: { cluster: 'app-west', sequence: 1 } },
    ]);

    const component = mount(
      <AccountRegionClusterSelector
        accounts={[]}
        application={application}
        component={componentModel}
        singleRegion={true}
      />,
    );
    await settle(component);

    expect(component.find('select.region-select option').map((option) => option.text())).toContain('us-west-2');

    component.find('select.region-select').simulate('change', { target: { value: 'us-west-2' } });

    expect(componentModel.cluster).toBeUndefined();
  });

  it('updates moniker from the selected cluster and notifies account changes', async () => {
    const onAccountUpdate = jasmine.createSpy('onAccountUpdate');
    const componentModel = { cloudProviderType: 'aws', credentials: 'test', region: 'us-east-1' } as any;
    const application = applicationWithServerGroups([
      { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
    ]);

    const component = mount(
      <AccountRegionClusterSelector
        accounts={['test', 'prod']}
        application={application}
        component={componentModel}
        onAccountUpdate={onAccountUpdate}
        singleRegion={true}
      />,
    );
    await settle(component);

    component.find('select.cluster-select').simulate('change', { target: { value: 'app-main' } });
    expect(componentModel.moniker).toEqual({ cluster: 'app-main', sequence: null });

    component
      .find('select.SelectInput')
      .simulate('change', { target: { value: 'prod' }, persist: jasmine.createSpy() });
    expect(componentModel.credentials).toBe('prod');
    expect(componentModel.cluster).toBeUndefined();
    expect(onAccountUpdate).toHaveBeenCalled();
  });

  it('renders expression credentials as runtime-resolved account text', async () => {
    const componentModel = { cloudProviderType: 'aws', credentials: '${parameters.account}' } as any;

    const component = mount(
      <AccountRegionClusterSelector
        accounts={['test', 'prod']}
        application={applicationWithServerGroups([])}
        component={componentModel}
      />,
    );
    await settle(component);

    expect(component.text()).toContain('Resolved at runtime from expression');
    expect(component.text()).toContain('${parameters.account}');
    expect(component.find('select.SelectInput').exists()).toBe(false);
  });

  it('supports entering arbitrary cluster text while using all provider regions', async () => {
    const componentModel = { cloudProviderType: 'aws', credentials: 'test', region: 'us-east-1' } as any;
    const application = applicationWithServerGroups([
      { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
    ]);

    const component = mount(
      <AccountRegionClusterSelector
        accounts={[]}
        application={application}
        component={componentModel}
        singleRegion={true}
      />,
    );
    await settle(component);

    component.find('button.cluster-text-toggle').simulate('click');

    expect(component.find('input.cluster-text-input').exists()).toBe(true);
    expect(component.find('select.region-select option').map((option) => option.text())).toContain('us-west-2');

    component.find('input.cluster-text-input').simulate('change', { target: { value: 'new-cluster' } });

    expect(componentModel.cluster).toBe('new-cluster');
    expect(componentModel.moniker).toBeUndefined();
  });

  it('clears cluster text and recomputes regions when toggled back to cluster select', async () => {
    const componentModel = {
      cloudProviderType: 'aws',
      credentials: 'test',
      region: 'us-east-1',
      cluster: 'typed',
    } as any;
    const application = applicationWithServerGroups([
      { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
    ]);

    const component = mount(
      <AccountRegionClusterSelector
        accounts={[]}
        application={application}
        component={componentModel}
        singleRegion={true}
      />,
    );
    await settle(component);

    component.find('button.cluster-select-toggle').simulate('click');

    expect(componentModel.cluster).toBeUndefined();
    expect(component.find('select.cluster-select').exists()).toBe(true);
    expect(component.find('select.region-select option').map((option) => option.text())).not.toContain('us-west-2');
  });

  it('reopens persisted custom cluster values in text input mode', async () => {
    const componentModel = {
      cloudProviderType: 'aws',
      credentials: 'test',
      region: 'us-east-1',
      cluster: 'persisted-custom-cluster',
    } as any;
    const application = applicationWithServerGroups([
      { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
    ]);

    const component = mount(
      <AccountRegionClusterSelector
        accounts={[]}
        application={application}
        component={componentModel}
        singleRegion={true}
      />,
    );
    await settle(component);

    expect(component.find('input.cluster-text-input').prop('value')).toBe('persisted-custom-cluster');
    expect(component.find('select.cluster-select').exists()).toBe(false);
  });

  it('shows all provider regions when no cluster is selected and current cluster is not in cluster list', async () => {
    const componentModel = { cloudProviderType: 'aws', credentials: 'test', region: 'us-east-1' } as any;
    const application = applicationWithServerGroups([
      { account: 'test', region: 'us-east-1', cluster: 'app-main', moniker: { cluster: 'app-main', sequence: 7 } },
    ]);

    const component = mount(
      <AccountRegionClusterSelector
        accounts={[]}
        application={application}
        component={componentModel}
        singleRegion={true}
      />,
    );
    await settle(component);

    expect(component.find('select.region-select option').map((option) => option.text())).toContain('us-west-2');
    expect(component.find('select.cluster-select').exists()).toBe(true);
  });
});

function applicationWithServerGroups(serverGroups: any[]) {
  return {
    getDataSource: () => ({ data: serverGroups }),
  } as any;
}

async function settle(component: any) {
  await Promise.resolve();
  await Promise.resolve();
  component.update();
}
