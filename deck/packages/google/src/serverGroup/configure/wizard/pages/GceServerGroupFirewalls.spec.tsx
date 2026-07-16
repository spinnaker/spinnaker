import { shallow } from 'enzyme';
import type { FormikProps } from 'formik';
import React from 'react';

import { GceServerGroupFirewalls } from './GceServerGroupFirewalls';
import type { IGceServerGroupCommand, IGceServerGroupWizardAdapter } from '../GceServerGroupWizard.types';

describe('GCE server group Firewalls page', () => {
  it('separates network scoped explicit and implicit firewalls and preserves unavailable selections', () => {
    const values = command({
      securityGroups: ['web-firewall', 'persisted-firewall', 'web-firewall'],
      viewState: { mode: 'clone', dirty: {}, listImplicitSecurityGroups: true },
    });
    const { formik } = testProps(values);
    const wrapper = shallow(<GceServerGroupFirewalls app={{} as any} formik={formik} />);

    expect(selectOptions(wrapper)).toEqual([
      ['api-firewall', 'api-firewall (api-firewall)'],
      ['web-firewall', 'web-firewall (web-firewall)'],
      ['persisted-firewall', 'persisted-firewall (unavailable)'],
    ]);
    expect(wrapper.find('select[aria-label="Firewalls"]').prop('value')).toEqual([
      'web-firewall',
      'persisted-firewall',
    ]);
    expect(wrapper.find('label[htmlFor="gce-server-group-firewalls"]').text()).toBe('Firewalls');
    expect(wrapper.find('label[htmlFor="gce-show-implicit-firewalls"]').text()).toContain('Show implicit firewalls');
    expect(wrapper.find('ul[aria-label="Implicit firewalls"] li').map((item) => item.text())).toEqual([
      'implicit-firewall',
    ]);
  });

  it('routes explicit firewall changes through networkChanged without losing selections or tags', async () => {
    const values = command({
      securityGroups: ['persisted-firewall'],
      tags: [{ value: 'existing-tag' }],
    });
    const { adapter, formik } = testProps(values);
    adapter.applyCommandHandler.and.callFake(async (nextCommand) => ({
      command: { ...nextCommand, securityGroups: [], tags: [] },
      result: { dirty: { securityGroups: ['persisted-firewall'] } },
    }));
    const wrapper = shallow(<GceServerGroupFirewalls app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('select[aria-label="Firewalls"]').simulate('change', {
      target: {
        selectedOptions: [{ value: 'web-firewall' }, { value: 'persisted-firewall' }, { value: 'web-firewall' }],
      },
    });
    await flush();

    const changedCommand = adapter.applyCommandHandler.calls.mostRecent().args[0];
    expect(changedCommand.securityGroups).toEqual(['web-firewall', 'persisted-firewall']);
    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(changedCommand, 'networkChanged');
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        securityGroups: ['web-firewall', 'persisted-firewall'],
        tags: [{ value: 'existing-tag' }],
      }),
    );
  });

  it('selecting a target tag adds every matching firewall once without discarding unrelated tags', async () => {
    const values = command({
      securityGroups: ['web-firewall'],
      tags: [{ value: 'existing-tag' }, { value: 'existing-tag' }],
    });
    const { adapter, formik } = testProps(values);
    const wrapper = shallow(<GceServerGroupFirewalls app={{} as any} formik={formik} adapter={adapter} />);

    wrapper
      .find('input[aria-label="Target tag shared for firewall web-firewall"]')
      .simulate('change', { target: { checked: true } });
    await flush();

    const changedCommand = adapter.applyCommandHandler.calls.mostRecent().args[0];
    expect(changedCommand.securityGroups).toEqual(['web-firewall', 'api-firewall']);
    expect(changedCommand.tags).toEqual([{ value: 'existing-tag' }, { value: 'shared' }]);
    expect(adapter.applyCommandHandler).toHaveBeenCalledWith(changedCommand, 'networkChanged');
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        securityGroups: ['web-firewall', 'api-firewall'],
        tags: [{ value: 'existing-tag' }, { value: 'shared' }],
      }),
    );
  });

  it('removing a target tag removes firewalls associated only by that tag but keeps unavailable references', async () => {
    const values = command({
      securityGroups: ['web-firewall', 'api-firewall', 'persisted-firewall'],
      tags: [{ value: 'existing-tag' }, { value: 'shared' }],
    });
    const { adapter, formik } = testProps(values);
    const wrapper = shallow(<GceServerGroupFirewalls app={{} as any} formik={formik} adapter={adapter} />);

    wrapper
      .find('input[aria-label="Target tag shared for firewall web-firewall"]')
      .simulate('change', { target: { checked: false } });
    await flush();

    const changedCommand = adapter.applyCommandHandler.calls.mostRecent().args[0];
    expect(changedCommand.securityGroups).toEqual(['persisted-firewall']);
    expect(changedCommand.tags).toEqual([{ value: 'existing-tag' }]);
  });

  it('refreshes firewall data while preserving explicit selections and target tags', async () => {
    const values = command({
      securityGroups: ['persisted-firewall'],
      tags: [{ value: 'existing-tag' }],
    });
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationRefresh.and.resolveTo({
      command: {
        ...values,
        backingData: { ...values.backingData, refreshed: true },
        securityGroups: [],
        tags: [],
      },
      result: { dirty: {} },
    });
    const wrapper = shallow(<GceServerGroupFirewalls app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('button[aria-label="Refresh firewalls"]').simulate('click');
    await flush();

    expect(adapter.applyConfigurationRefresh).toHaveBeenCalledWith(values, 'refreshSecurityGroups');
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        backingData: jasmine.objectContaining({ refreshed: true }),
        securityGroups: ['persisted-firewall'],
        tags: [{ value: 'existing-tag' }],
      }),
    );
  });
});

function selectOptions(wrapper: ReturnType<typeof shallow>): string[][] {
  return wrapper
    .find('select[aria-label="Firewalls"] option')
    .map((option) => [option.prop('value') as string, option.text()]);
}

function testProps(values = command()) {
  const formik = ({
    values,
    setFieldValue: jasmine.createSpy('setFieldValue'),
    setValues: jasmine.createSpy('setValues'),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  const adapter = ({
    applyCommandHandler: jasmine
      .createSpy('applyCommandHandler')
      .and.callFake(async (nextCommand: IGceServerGroupCommand) => ({ command: nextCommand, result: { dirty: {} } })),
    applyConfigurationRefresh: jasmine
      .createSpy('applyConfigurationRefresh')
      .and.callFake(async (nextCommand: IGceServerGroupCommand) => ({ command: nextCommand, result: { dirty: {} } })),
  } as unknown) as jasmine.SpyObj<IGceServerGroupWizardAdapter>;
  return { adapter, formik };
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    credentials: 'account-a',
    regional: false,
    region: 'us-central1',
    network: 'default',
    securityGroups: [],
    tags: [],
    backingData: {
      filtered: {},
      securityGroups: {
        'account-a': {
          gce: {
            global: [
              { id: 'web-firewall', name: 'web-firewall', network: 'default', targetTags: '[web, shared]' },
              { id: 'api-firewall', name: 'api-firewall', network: 'default', targetTags: ['api', 'shared'] },
              { id: 'implicit-firewall', name: 'implicit-firewall', network: 'default', targetTags: [] },
              { id: 'other-network-firewall', name: 'other-network-firewall', network: 'other', targetTags: ['web'] },
              { id: 'web-firewall', name: 'web-firewall', network: 'default', targetTags: ['web'] },
            ],
          },
        },
        'account-b': {
          gce: {
            global: [{ id: 'other-account-firewall', network: 'default', targetTags: ['web'] }],
          },
        },
      },
    },
    viewState: { mode: 'create', dirty: {} },
    ...overrides,
  };
}

function flush(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve));
}
