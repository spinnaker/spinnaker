import { mount } from 'enzyme';
import React from 'react';

import type { IApplicationAttributes } from '../service/ApplicationWriter';
import { ApplicationProviderFields } from './ApplicationProviderFields';
import { CloudProviderRegistry } from '../../cloudProvider';
import { SETTINGS } from '../../config/settings';
import { HelpField } from '../../help/HelpField';

describe('ApplicationProviderFields', () => {
  const firstProvider = 'applicationFieldsFirst';
  const secondProvider = 'applicationFieldsSecond';

  beforeEach(() => {
    SETTINGS.providers[firstProvider] = { enabledByDefault: true };
    SETTINGS.providers[secondProvider] = { associateAddress: false };
    CloudProviderRegistry.registerProvider(firstProvider, {
      name: 'First',
      applicationProviderFields: [{ field: 'enabledByDefault', label: 'Enabled by default', type: 'boolean' }],
    });
    CloudProviderRegistry.registerProvider(secondProvider, {
      name: 'Second',
      applicationProviderFields: [
        {
          field: 'associateAddress',
          helpKey: 'second.associateAddress',
          label: 'Associate address',
          type: 'boolean',
        },
      ],
    });
  });

  afterEach(() => {
    delete SETTINGS.providers[firstProvider];
    delete SETTINGS.providers[secondProvider];
    (CloudProviderRegistry as any).providers.delete(firstProvider);
    (CloudProviderRegistry as any).providers.delete(secondProvider);
  });

  it('uses selected unique providers when providers are selected', () => {
    const wrapper = mount(
      <ApplicationProviderFields
        application={{ name: 'app' }}
        availableProviders={[firstProvider, secondProvider]}
        selectedProviders={[secondProvider, secondProvider]}
        onChange={() => undefined}
      />,
    );

    expect(wrapper.find('input[type="checkbox"]').length).toBe(1);
    expect(wrapper.find('input').prop('data-provider')).toBe(secondProvider);
  });

  it('uses all unique available providers when none are selected', () => {
    const wrapper = mount(
      <ApplicationProviderFields
        application={{ name: 'app' }}
        availableProviders={[firstProvider, firstProvider, secondProvider]}
        selectedProviders={[]}
        onChange={() => undefined}
      />,
    );

    expect(wrapper.find('input[type="checkbox"]').length).toBe(2);
  });

  it('clones the application and initializes configured defaults only when values are absent', () => {
    const application: IApplicationAttributes = {
      name: 'app',
      providerSettings: { [secondProvider]: { associateAddress: true } },
    };
    const onChange = jasmine.createSpy('onChange');

    mount(
      <ApplicationProviderFields
        application={application}
        availableProviders={[firstProvider, secondProvider]}
        selectedProviders={[]}
        onChange={onChange}
      />,
    );

    expect(application.providerSettings[firstProvider]).toBeUndefined();
    expect(onChange).toHaveBeenCalledTimes(1);
    const changed = onChange.calls.mostRecent().args[0];
    expect(changed).not.toBe(application);
    expect(changed.providerSettings[firstProvider].enabledByDefault).toBe(true);
    expect(changed.providerSettings[secondProvider].associateAddress).toBe(true);
  });

  it('writes checkbox values to the nested provider setting on a clone', () => {
    const application: IApplicationAttributes = {
      name: 'app',
      providerSettings: { [firstProvider]: { enabledByDefault: true } },
    };
    const onChange = jasmine.createSpy('onChange');
    const wrapper = mount(
      <ApplicationProviderFields
        application={application}
        availableProviders={[firstProvider]}
        selectedProviders={[firstProvider]}
        onChange={onChange}
      />,
    );

    wrapper.find('input[type="checkbox"]').simulate('change', { target: { checked: false } });

    const changed = onChange.calls.mostRecent().args[0];
    expect(changed).not.toBe(application);
    expect(changed.providerSettings[firstProvider].enabledByDefault).toBe(false);
    expect(application.providerSettings[firstProvider].enabledByDefault).toBe(true);
  });

  it('renders provider help metadata with its boolean field', () => {
    const wrapper = mount(
      <ApplicationProviderFields
        application={{ name: 'app', providerSettings: { [secondProvider]: { associateAddress: false } } }}
        availableProviders={[secondProvider]}
        selectedProviders={[secondProvider]}
        onChange={() => undefined}
      />,
    );

    expect(wrapper.text()).toContain('Associate address');
    expect(wrapper.find(HelpField).prop('id')).toBe('second.associateAddress');
    expect(wrapper.find('input').prop('checked')).toBe(false);
  });
});
