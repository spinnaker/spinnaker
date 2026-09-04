import { shallow } from 'enzyme';
import type { FormikProps } from 'formik';
import React from 'react';

import { GceServerGroupLoadBalancers } from './GceServerGroupLoadBalancers';
import type { IGceServerGroupCommand, IGceServerGroupWizardAdapter } from '../GceServerGroupWizard.types';
import { validateGceServerGroupCommand } from '../GceServerGroupWizard.helpers';
import { transformGceServerGroupCommand } from '../GceCloneServerGroupModal';

describe('GCE server group Load Balancers page', () => {
  it('shows only account and region scoped load balancers without duplicate references', () => {
    const values = command({ loadBalancers: ['regional-lb', 'persisted-lb', 'persisted-lb'] });
    const { formik } = testProps(values);
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} />);

    expect(selectOptions(wrapper)).toEqual([
      ['global-lb', 'global-lb'],
      ['regional-lb', 'regional-lb'],
      ['persisted-lb', 'persisted-lb (unavailable)'],
    ]);
    expect(wrapper.find('label[htmlFor="gce-server-group-load-balancers"]').text()).toBe('Load Balancers');
    expect(wrapper.find('select[aria-label="Load balancers"]').prop('value')).toEqual(['regional-lb', 'persisted-lb']);
  });

  it('does not fall back to stale filtered load balancers when raw data has no scoped matches', () => {
    const values = command({ credentials: 'account-c' });
    values.backingData.filtered.loadBalancers = ['stale-lb'];
    const { formik } = testProps(values);
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} />);

    expect(selectOptions(wrapper)).toEqual([]);
  });

  it('runs load-balancer configuration and preserves selected unavailable references', async () => {
    const values = command({ loadBalancers: ['persisted-lb'] });
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
      command: { ...nextCommand, loadBalancers: ['regional-lb'], backendServices: { 'regional-lb': ['backend'] } },
      result: { dirty: { loadBalancers: ['persisted-lb'] } },
    }));
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
      target: { selectedOptions: [{ value: 'regional-lb' }, { value: 'persisted-lb' }, { value: 'regional-lb' }] },
    });
    await flush();

    const changedCommand = adapter.applyConfigurationUpdate.calls.mostRecent().args[0];
    expect(changedCommand.loadBalancers).toEqual(['regional-lb']);
    expect(adapter.applyConfigurationUpdate).toHaveBeenCalledWith(changedCommand, 'configureLoadBalancerOptions');
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        backendServices: { 'regional-lb': ['backend'] },
        loadBalancers: ['regional-lb', 'persisted-lb'],
      }),
    );
  });

  it('initializes the backend-compatible default policy when selecting the first supported load balancer', async () => {
    const values = command();
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
      command: { ...nextCommand, loadBalancingPolicy: undefined },
      result: { dirty: {} },
    }));
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
      target: { selectedOptions: [{ value: 'regional-lb' }] },
    });
    await flush();

    expect(adapter.applyConfigurationUpdate.calls.mostRecent().args[0].loadBalancingPolicy).toEqual({
      balancingMode: 'UTILIZATION',
      capacityScaler: 1,
      maxUtilization: 0.8,
      namedPorts: [{ name: 'http', port: 80 }],
    });
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        loadBalancingPolicy: {
          balancingMode: 'UTILIZATION',
          capacityScaler: 1,
          maxUtilization: 0.8,
          namedPorts: [{ name: 'http', port: 80 }],
        },
      }),
    );
  });

  it('refreshes backing data while preserving selected load balancers', async () => {
    const values = command({
      loadBalancers: ['persisted-lb'],
      loadBalancerSelectionMetadata: {
        'persisted-lb': { key: 'load-balancer-names', names: ['persisted-listener'] },
      },
      viewState: {
        mode: 'create',
        dirty: { loadBalancers: ['regional-lb'] },
        initialLoadBalancers: ['regional-lb', 'persisted-lb'],
        loadBalancerSelectionsChanged: true,
      },
    });
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationRefresh.and.resolveTo({
      command: {
        ...values,
        backingData: { ...values.backingData, refreshed: true },
        loadBalancers: [],
        loadBalancerSelectionMetadata: {},
        viewState: {
          mode: 'create',
          dirty: {},
        },
      },
      result: { dirty: { loadBalancers: ['persisted-lb'] } },
    });
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('button[aria-label="Refresh load balancers"]').simulate('click');
    await flush();

    expect(adapter.applyConfigurationRefresh).toHaveBeenCalledWith(values, 'refreshLoadBalancers');
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        backingData: jasmine.objectContaining({ refreshed: true }),
        loadBalancers: ['persisted-lb'],
        loadBalancerSelectionMetadata: {
          'persisted-lb': { key: 'load-balancer-names', names: ['persisted-listener'] },
        },
        viewState: jasmine.objectContaining({
          dirty: { loadBalancers: ['persisted-lb'] },
          initialLoadBalancers: ['regional-lb', 'persisted-lb'],
          loadBalancerSelectionsChanged: true,
        }),
      }),
    );
  });

  it('preserves inline typed selections across a refresh', async () => {
    const values = command({
      loadBalancers: [{ name: 'inline-ssl-lb', loadBalancerType: 'SSL' }, 'persisted-lb', 'persisted-lb'],
    });
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationRefresh.and.resolveTo({
      command: { ...values, loadBalancers: [] },
      result: { dirty: {} },
    });
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('button[aria-label="Refresh load balancers"]').simulate('click');
    await flush();

    // The inline object carries the type the load balancer index cannot supply; coercing selections
    // to plain strings on refresh silently drops it.
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        loadBalancers: [{ name: 'inline-ssl-lb', loadBalancerType: 'SSL' }, 'persisted-lb'],
      }),
    );
  });

  it('shows scoped backend services, named-port names, and balancing modes while preserving unavailable values', () => {
    const values = command({
      loadBalancers: ['regional-lb'],
      backendServices: { 'regional-lb': ['backend-a', 'persisted-backend'] },
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: 0.8,
        maxRatePerInstance: 50,
        namedPorts: [
          { name: 'http', port: 8080 },
          { name: 'persisted-port', port: 9000 },
        ],
      },
    });
    const { formik } = testProps(values);
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} />);

    expect(selectOptions(wrapper, 'Backend services for regional-lb')).toEqual([
      ['backend-a', 'backend-a'],
      ['backend-b', 'backend-b'],
      ['persisted-backend', 'persisted-backend (unavailable)'],
    ]);
    expect(selectOptions(wrapper, 'Named port name 1')).toEqual([
      ['http', 'http'],
      ['metrics', 'metrics'],
    ]);
    expect(selectOptions(wrapper, 'Named port name 2')).toEqual([
      ['http', 'http'],
      ['metrics', 'metrics'],
      ['persisted-port', 'persisted-port (unavailable)'],
    ]);
    expect(selectOptions(wrapper, 'Balancing mode')).toEqual([
      ['RATE', 'RATE'],
      ['UTILIZATION', 'UTILIZATION'],
    ]);
    expect(wrapper.find('input[aria-label="Capacity scaler"]').prop('value')).toBe(80);
    expect(wrapper.find('input[aria-label="Max rate per instance"]').prop('value')).toBe(50);
  });

  it('round-trips load-balancer metadata without dropping unavailable or unrelated references', async () => {
    const values = command({
      loadBalancers: ['regional-lb', 'persisted-lb'],
      loadBalancerMetadata: {
        'global-load-balancer-names': ['regional-listener', 'persisted-listener'],
        'load-balancer-names': ['unrelated-regional-listener'],
      },
    });
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
      command: { ...nextCommand, loadBalancers: ['global-lb'], loadBalancerMetadata: {} },
      result: { dirty: { loadBalancers: ['persisted-lb'] } },
    }));
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
      target: { selectedOptions: [{ value: 'global-lb' }, { value: 'persisted-lb' }] },
    });
    await flush();

    const changedCommand = adapter.applyConfigurationUpdate.calls.mostRecent().args[0];
    expect(changedCommand.loadBalancerMetadata).toEqual({
      'global-load-balancer-names': ['persisted-listener', 'global-lb'],
      'load-balancer-names': ['unrelated-regional-listener'],
    });
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        loadBalancers: ['global-lb', 'persisted-lb'],
        loadBalancerMetadata: {
          'global-load-balancer-names': ['persisted-listener', 'global-lb'],
          'load-balancer-names': ['unrelated-regional-listener'],
        },
      }),
    );
  });

  it('configures only resolvable load balancers and restores unavailable selections and unrelated backend mappings', async () => {
    const values = command({
      loadBalancers: ['regional-lb', 'persisted-lb'],
      backendServices: {
        'regional-lb': ['old-backend'],
        'persisted-lb': ['persisted-backend'],
        'other-lb': ['other-backend'],
      },
      backendServiceMetadata: ['old-backend', 'persisted-backend', 'other-backend'],
    });
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
      command: {
        ...nextCommand,
        backendServices: { 'global-lb': ['configured-backend'] },
        backendServiceMetadata: ['configured-backend'],
      },
      result: { dirty: {} },
    }));
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
      target: { selectedOptions: [{ value: 'global-lb' }, { value: 'persisted-lb' }] },
    });
    await flush();

    expect(adapter.applyConfigurationUpdate.calls.mostRecent().args[0].loadBalancers).toEqual(['global-lb']);
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        loadBalancers: ['global-lb', 'persisted-lb'],
        backendServices: {
          'persisted-lb': ['persisted-backend'],
          'other-lb': ['other-backend'],
          'global-lb': ['configured-backend'],
        },
        backendServiceMetadata: ['persisted-backend', 'other-backend', 'configured-backend'],
      }),
    );
  });

  it('round-trips backend mappings and backend metadata without dropping other references', async () => {
    const values = command({
      loadBalancers: ['regional-lb'],
      backendServices: {
        'regional-lb': ['backend-a', 'persisted-backend'],
        'other-lb': ['other-backend'],
      },
      backendServiceMetadata: ['backend-a', 'persisted-backend', 'other-backend'],
    });
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
      command: {
        ...nextCommand,
        backendServices: { 'regional-lb': ['backend-b'] },
        backendServiceMetadata: ['backend-b'],
      },
      result: { dirty: {} },
    }));
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('select[aria-label="Backend services for regional-lb"]').simulate('change', {
      target: { selectedOptions: [{ value: 'backend-b' }, { value: 'persisted-backend' }] },
    });
    await flush();

    const changedCommand = adapter.applyConfigurationUpdate.calls.mostRecent().args[0];
    expect(changedCommand.backendServices).toEqual({
      'regional-lb': ['backend-b', 'persisted-backend'],
      'other-lb': ['other-backend'],
    });
    expect(changedCommand.backendServiceMetadata).toEqual(['backend-b', 'persisted-backend', 'other-backend']);
    expect(formik.setValues).toHaveBeenCalledWith(
      jasmine.objectContaining({
        backendServices: {
          'regional-lb': ['backend-b', 'persisted-backend'],
          'other-lb': ['other-backend'],
        },
        backendServiceMetadata: ['backend-b', 'persisted-backend', 'other-backend'],
      }),
    );
  });

  it('updates named ports and balancing mode through the generic adapter while retaining policy fields', async () => {
    const values = command({
      loadBalancers: ['regional-lb'],
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: 1,
        maxRatePerInstance: 50,
        maxConnectionsPerInstance: 20,
        namedPorts: [{ name: 'http', port: 8080 }],
        unknownField: 'keep',
      },
    });
    const { adapter, formik } = testProps(values);
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    wrapper.find('select[aria-label="Balancing mode"]').simulate('change', { target: { value: 'UTILIZATION' } });
    await flush();

    const changedCommand = adapter.applyConfigurationUpdate.calls.mostRecent().args[0];
    expect(changedCommand.loadBalancingPolicy).toEqual({
      balancingMode: 'UTILIZATION',
      capacityScaler: 1,
      namedPorts: [{ name: 'http', port: 8080 }],
      unknownField: 'keep',
    });
    expect(adapter.applyConfigurationUpdate).toHaveBeenCalledWith(changedCommand, 'configureLoadBalancerOptions');
    expect(formik.setValues).toHaveBeenCalledWith(jasmine.objectContaining(changedCommand));
  });

  it('derives balancing modes from selected load-balancer types and renders the active limit', () => {
    const connectionValues = command({
      loadBalancers: ['global-lb'],
      loadBalancingPolicy: {
        balancingMode: 'CONNECTION',
        capacityScaler: 1,
        maxConnectionsPerInstance: 25,
        namedPorts: [],
      },
    });
    const connectionWrapper = shallow(
      <GceServerGroupLoadBalancers app={{} as any} formik={testProps(connectionValues).formik} />,
    );
    expect(selectOptions(connectionWrapper, 'Balancing mode')).toEqual([
      ['CONNECTION', 'CONNECTION'],
      ['UTILIZATION', 'UTILIZATION'],
    ]);
    expect(connectionWrapper.find('input[aria-label="Max connections per instance"]').prop('value')).toBe(25);

    const mixedValues = command({
      loadBalancers: ['regional-lb', 'global-lb'],
      loadBalancingPolicy: {
        balancingMode: 'UTILIZATION',
        capacityScaler: 1,
        maxUtilization: 0.7,
        namedPorts: [],
      },
    });
    const mixedWrapper = shallow(
      <GceServerGroupLoadBalancers app={{} as any} formik={testProps(mixedValues).formik} />,
    );
    expect(selectOptions(mixedWrapper, 'Balancing mode')).toEqual([['UTILIZATION', 'UTILIZATION']]);
    expect(mixedWrapper.find('input[aria-label="Max utilization"]').prop('value')).toBe(70);
  });

  it('round-trips named-port and percentage edits and supports adding and removing mappings', async () => {
    const values = command({
      loadBalancers: ['regional-lb'],
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: 1,
        maxRatePerInstance: 50,
        namedPorts: [{ name: 'http', port: 8080 }],
      },
    });
    const nameProps = testProps(values);
    const nameWrapper = shallow(
      <GceServerGroupLoadBalancers app={{} as any} formik={nameProps.formik} adapter={nameProps.adapter} />,
    );
    nameWrapper.find('select[aria-label="Named port name 1"]').simulate('change', { target: { value: 'metrics' } });
    await flush();
    expect(
      nameProps.adapter.applyConfigurationUpdate.calls.mostRecent().args[0].loadBalancingPolicy.namedPorts,
    ).toEqual([{ name: 'metrics', port: 8080 }]);

    const capacityProps = testProps(values);
    const capacityWrapper = shallow(
      <GceServerGroupLoadBalancers app={{} as any} formik={capacityProps.formik} adapter={capacityProps.adapter} />,
    );
    capacityWrapper.find('input[aria-label="Capacity scaler"]').simulate('change', { target: { value: '75' } });
    await flush();
    expect(
      capacityProps.adapter.applyConfigurationUpdate.calls.mostRecent().args[0].loadBalancingPolicy.capacityScaler,
    ).toBe(0.75);

    const addProps = testProps(values);
    const addWrapper = shallow(
      <GceServerGroupLoadBalancers app={{} as any} formik={addProps.formik} adapter={addProps.adapter} />,
    );
    addWrapper.find('button[aria-label="Add named port"]').simulate('click');
    await flush();
    expect(addProps.adapter.applyConfigurationUpdate.calls.mostRecent().args[0].loadBalancingPolicy.namedPorts).toEqual(
      [
        { name: 'http', port: 8080 },
        { name: '', port: 80 },
      ],
    );

    const removeProps = testProps(values);
    const removeWrapper = shallow(
      <GceServerGroupLoadBalancers app={{} as any} formik={removeProps.formik} adapter={removeProps.adapter} />,
    );
    removeWrapper.find('button[aria-label="Remove named port 1"]').simulate('click');
    await flush();
    expect(
      removeProps.adapter.applyConfigurationUpdate.calls.mostRecent().args[0].loadBalancingPolicy.namedPorts,
    ).toEqual([]);
  });

  it('renders policy validation errors and associates them with the invalid controls', () => {
    const rateWrapper = shallow(
      <GceServerGroupLoadBalancers
        app={{} as any}
        formik={
          testProps(
            command({
              loadBalancers: ['regional-lb'],
              loadBalancingPolicy: {
                balancingMode: 'RATE',
                capacityScaler: 2,
                maxRatePerInstance: -1,
                namedPorts: [{ name: '', port: 65536 }],
              },
            }),
          ).formik
        }
      />,
    );

    expectPolicyError(rateWrapper, 'input[aria-label="Capacity scaler"]', 'capacity-scaler', 'Capacity must be');
    expectPolicyError(
      rateWrapper,
      'select[aria-label="Named port name 1"]',
      'named-port-name-0',
      'Port name required.',
    );
    expectPolicyError(
      rateWrapper,
      'input[aria-label="Named port number 1"]',
      'named-port-port-0',
      'Port must be an integer',
    );
    expectPolicyError(
      rateWrapper,
      'input[aria-label="Max rate per instance"]',
      'max-rate-per-instance',
      'Max rate must be',
    );

    const modeWrapper = shallow(
      <GceServerGroupLoadBalancers
        app={{} as any}
        formik={
          testProps(
            command({
              loadBalancers: ['regional-lb'],
              loadBalancingPolicy: { balancingMode: '', capacityScaler: 1, namedPorts: [] },
            }),
          ).formik
        }
      />,
    );
    expectPolicyError(modeWrapper, 'select[aria-label="Balancing mode"]', 'balancing-mode', 'Select a balancing mode');

    const connectionWrapper = shallow(
      <GceServerGroupLoadBalancers
        app={{} as any}
        formik={
          testProps(
            command({
              loadBalancers: ['global-lb'],
              loadBalancingPolicy: {
                balancingMode: 'CONNECTION',
                capacityScaler: 1,
                maxConnectionsPerInstance: -1,
                namedPorts: [],
              },
            }),
          ).formik
        }
      />,
    );
    expectPolicyError(
      connectionWrapper,
      'input[aria-label="Max connections per instance"]',
      'max-connections-per-instance',
      'Max connections must be',
    );

    const utilizationWrapper = shallow(
      <GceServerGroupLoadBalancers
        app={{} as any}
        formik={
          testProps(
            command({
              loadBalancers: ['regional-lb'],
              loadBalancingPolicy: {
                balancingMode: 'UTILIZATION',
                capacityScaler: 1,
                maxUtilization: 2,
                namedPorts: [],
              },
            }),
          ).formik
        }
      />,
    );
    expectPolicyError(
      utilizationWrapper,
      'input[aria-label="Max utilization"]',
      'max-utilization',
      'Max utilization must be',
    );
  });

  it('treats EXTERNAL_MANAGED as an HTTP-family load balancer with regional listener metadata', async () => {
    const values = command({
      loadBalancers: ['external-managed-lb'],
      backendServices: { 'external-managed-lb': ['external-backend'] },
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: 1,
        maxRatePerInstance: 100,
        namedPorts: [{ name: 'https', port: 443 }],
      },
      backingData: {
        loadBalancers: [
          {
            accounts: [
              {
                name: 'account-a',
                regions: [
                  {
                    loadBalancers: [
                      { name: 'external-managed-lb', region: 'us-central1' },
                      { name: 'other-region-external-managed', region: 'europe-west1' },
                    ],
                  },
                ],
              },
            ],
          },
        ],
        filtered: {
          loadBalancerIndex: {
            'external-managed-lb': {
              name: 'external-managed-lb',
              loadBalancerType: 'EXTERNAL_MANAGED',
              listeners: [{ name: 'external-listener' }],
              backendServices: [
                { name: 'external-backend', portName: 'https' },
                { name: 'other-backend', portName: 'metrics' },
              ],
            },
            'other-region-external-managed': {
              name: 'other-region-external-managed',
              loadBalancerType: 'EXTERNAL_MANAGED',
              listeners: [{ name: 'other-region-listener' }],
              backendServices: [{ name: 'other-region-backend', portName: 'https' }],
            },
          },
        },
      },
    });
    const { adapter, formik } = testProps(values);
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    expect(selectOptions(wrapper)).toEqual([['external-managed-lb', 'external-managed-lb']]);
    expect(selectOptions(wrapper, 'Balancing mode')).toEqual([
      ['RATE', 'RATE'],
      ['UTILIZATION', 'UTILIZATION'],
    ]);
    expect(selectOptions(wrapper, 'Backend services for external-managed-lb')).toEqual([
      ['external-backend', 'external-backend'],
      ['other-backend', 'other-backend'],
    ]);

    adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
      command: { ...nextCommand, loadBalancers: ['external-managed-lb'] },
      result: { dirty: {} },
    }));
    wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
      target: { selectedOptions: [{ value: 'external-managed-lb' }] },
    });
    await flush();

    expect(adapter.applyConfigurationUpdate.calls.mostRecent().args[0].loadBalancerMetadata).toEqual({
      'load-balancer-names': ['external-listener'],
    });
  });

  it('does not expose load-balancing policy controls for REGIONAL_EXTERNAL_NETWORK and clears stale policy', async () => {
    const values = command({
      loadBalancers: ['regional-network-lb'],
      loadBalancingPolicy: {
        balancingMode: 'UTILIZATION',
        capacityScaler: 1,
        maxUtilization: 0.8,
        namedPorts: [{ name: 'http', port: 80 }],
      },
      backingData: {
        ...command().backingData,
        filtered: {
          loadBalancerIndex: {
            'regional-network-lb': {
              name: 'regional-network-lb',
              loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
              backendServices: ['network-backend'],
            },
          },
        },
      },
    });
    const { adapter, formik } = testProps(values);
    adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
      command: { ...nextCommand, loadBalancingPolicy: nextCommand.loadBalancingPolicy },
      result: { dirty: {} },
    }));
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

    expect(wrapper.find('select[aria-label="Balancing mode"]').exists()).toBe(false);
    expect(wrapper.find('input[aria-label="Capacity scaler"]').exists()).toBe(false);
    expect(selectOptions(wrapper, 'Backend services for regional-network-lb')).toEqual([]);

    wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
      target: { selectedOptions: [{ value: 'regional-network-lb' }] },
    });
    await flush();

    expect(adapter.applyConfigurationUpdate.calls.mostRecent().args[0].loadBalancingPolicy).toBeUndefined();
    expect(adapter.applyConfigurationUpdate.calls.mostRecent().args[0].loadBalancerMetadata).toEqual({
      'load-balancer-names': ['regional-network-lb'],
    });
  });

  it('does not reject REGIONAL_EXTERNAL_NETWORK-only commands with stale ignored loadBalancingPolicy before interaction', () => {
    const values = regionalExternalNetworkCommand({
      loadBalancingPolicy: {
        balancingMode: 'UTILIZATION',
        capacityScaler: 2,
        maxUtilization: 2,
        namedPorts: [{ name: '', port: 65536 }],
      },
    });
    const page = shallow(
      <GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />,
    ).instance() as GceServerGroupLoadBalancers;

    expect(page.validate(values)).toEqual({});

    const submitCommand = {
      ...values,
      application: 'fnord',
      capacity: { desired: 3, max: 3, min: 3 },
      image: 'ubuntu',
      zone: 'us-central1-a',
    };
    expect(validateGceServerGroupCommand(submitCommand)).toEqual({});
    expect(transformGceServerGroupCommand(submitCommand).loadBalancingPolicy).toBeUndefined();
  });

  (['HTTP', 'INTERNAL_MANAGED', 'EXTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    it(`allows only RATE for ${loadBalancerType} mixed with REGIONAL_EXTERNAL_NETWORK`, () => {
      const values = command({
        loadBalancers: ['http-lb', 'regional-network-lb'],
        loadBalancingPolicy: {
          balancingMode: 'RATE',
          capacityScaler: 1,
          maxRatePerInstance: 100,
          namedPorts: [{ name: 'https', port: 443 }],
        },
        backingData: {
          ...command().backingData,
          filtered: {
            loadBalancerIndex: {
              'http-lb': {
                name: 'http-lb',
                loadBalancerType,
                listeners: [{ name: 'http-listener' }],
                backendServices: [{ name: 'http-backend', portName: 'https' }],
              },
              'regional-network-lb': {
                name: 'regional-network-lb',
                loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
              },
            },
          },
        },
      });
      const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);

      expect(selectOptions(wrapper, 'Balancing mode')).toEqual([['RATE', 'RATE']]);
      expect(wrapper.find('input[aria-label="Max rate per instance"]').prop('value')).toBe(100);
    });
  });

  (['SSL', 'TCP'] as const).forEach((loadBalancerType) => {
    it(`allows only CONNECTION for ${loadBalancerType} mixed with REGIONAL_EXTERNAL_NETWORK`, () => {
      const values = command({
        loadBalancers: ['proxy-lb', 'regional-network-lb'],
        loadBalancingPolicy: {
          balancingMode: 'CONNECTION',
          capacityScaler: 1,
          maxConnectionsPerInstance: 25,
          namedPorts: [],
        },
        backingData: {
          ...command().backingData,
          filtered: {
            loadBalancerIndex: {
              'proxy-lb': {
                name: 'proxy-lb',
                loadBalancerType,
              },
              'regional-network-lb': {
                name: 'regional-network-lb',
                loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
              },
            },
          },
        },
      });
      const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);

      expect(selectOptions(wrapper, 'Balancing mode')).toEqual([['CONNECTION', 'CONNECTION']]);
      expect(wrapper.find('input[aria-label="Max connections per instance"]').prop('value')).toBe(25);
    });

    it(`rejects HTTP, ${loadBalancerType}, and REGIONAL_EXTERNAL_NETWORK because no balancing mode is shared`, () => {
      const values = command({
        application: 'fnord',
        capacity: { desired: 3, max: 3, min: 3 },
        image: 'ubuntu',
        zone: 'us-central1-a',
        loadBalancers: ['http-lb', 'proxy-lb', 'regional-network-lb'],
        loadBalancingPolicy: {
          balancingMode: 'UTILIZATION',
          capacityScaler: 1,
          maxUtilization: 0.8,
          namedPorts: [{ name: 'https', port: 443 }],
        },
        backingData: {
          ...command().backingData,
          filtered: {
            loadBalancerIndex: {
              'http-lb': { name: 'http-lb', loadBalancerType: 'HTTP' },
              'proxy-lb': { name: 'proxy-lb', loadBalancerType },
              'regional-network-lb': {
                name: 'regional-network-lb',
                loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
              },
            },
          },
        },
      });
      const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);
      const page = wrapper.instance() as GceServerGroupLoadBalancers;

      expect((page.validate(values) as any).loadBalancingPolicy?.balancingMode).toContain(
        'no compatible balancing mode',
      );
      expect((validateGceServerGroupCommand(values) as any).loadBalancingPolicy?.balancingMode).toContain(
        'no compatible balancing mode',
      );
      expect(wrapper.find('[role="alert"]').text()).toContain('no compatible balancing mode');
    });
  });

  (['HTTP', 'INTERNAL_MANAGED', 'EXTERNAL_MANAGED'] as const).forEach((loadBalancerType) => {
    it(`allows only RATE for ${loadBalancerType} mixed with INTERNAL passthrough`, () => {
      const values = command({
        loadBalancers: ['http-lb', 'internal-lb'],
        loadBalancingPolicy: {
          balancingMode: 'RATE',
          capacityScaler: 1,
          maxRatePerInstance: 100,
          namedPorts: [{ name: 'https', port: 443 }],
        },
        backingData: {
          ...command().backingData,
          filtered: {
            loadBalancerIndex: {
              'http-lb': {
                name: 'http-lb',
                loadBalancerType,
                listeners: [{ name: 'http-listener' }],
                backendServices: [{ name: 'http-backend', portName: 'https' }],
              },
              'internal-lb': { name: 'internal-lb', loadBalancerType: 'INTERNAL' },
            },
          },
        },
      });
      const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);

      // BasicGoogleDeployHandler counts INTERNAL as passthrough, so offering UTILIZATION here would
      // let the wizard submit a combination Clouddriver rejects.
      expect(selectOptions(wrapper, 'Balancing mode')).toEqual([['RATE', 'RATE']]);
    });
  });

  it('does not block an unresolved load balancer when no passthrough is selected', () => {
    const values = command({
      application: 'fnord',
      capacity: { desired: 3, max: 3, min: 3 },
      image: 'ubuntu',
      zone: 'us-central1-a',
      loadBalancers: ['http-lb', 'unresolved-lb'],
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: 1,
        maxRatePerInstance: 100,
        namedPorts: [{ name: 'http', port: 80 }],
      },
      backingData: {
        ...command().backingData,
        filtered: {
          loadBalancerIndex: {
            'http-lb': {
              name: 'http-lb',
              loadBalancerType: 'HTTP',
              listeners: [{ name: 'http-listener' }],
              backendServices: [{ name: 'http-backend', portName: 'http' }],
            },
          },
        },
      },
    });
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);

    // Only a passthrough backend makes the required mode depend on the other family, so without one
    // an unidentifiable selection must not block the submit.
    expect(selectOptions(wrapper, 'Balancing mode')).toEqual([
      ['RATE', 'RATE'],
      ['UTILIZATION', 'UTILIZATION'],
    ]);
    expect((wrapper.instance() as GceServerGroupLoadBalancers).validate(values)).toEqual({});
    expect(validateGceServerGroupCommand(values)).toEqual({});
  });

  it('normalizes inline typed load balancers when deriving compatibility', () => {
    const values = command({
      loadBalancers: [
        { name: 'http-lb', loadBalancerType: 'HTTP' },
        { name: 'regional-network-lb', loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK' },
      ],
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: 1,
        maxRatePerInstance: 100,
        namedPorts: [{ name: 'http', port: 80 }],
      },
      backingData: {
        ...command().backingData,
        filtered: { loadBalancerIndex: {} },
      },
    });
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);

    expect(selectOptions(wrapper, 'Balancing mode')).toEqual([['RATE', 'RATE']]);
    expect((wrapper.instance() as GceServerGroupLoadBalancers).validate(values)).toEqual({});
  });

  it('blocks an unresolved load balancer combined with REGIONAL_EXTERNAL_NETWORK', () => {
    const values = regionalExternalNetworkCommand({
      application: 'fnord',
      capacity: { desired: 3, max: 3, min: 3 },
      image: 'ubuntu',
      zone: 'us-central1-a',
      loadBalancers: ['regional-network-lb', 'unresolved-lb'],
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: 1,
        maxRatePerInstance: 100,
        namedPorts: [{ name: 'http', port: 80 }],
      },
    });
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);

    expect(
      ((wrapper.instance() as GceServerGroupLoadBalancers).validate(values) as any).loadBalancingPolicy?.balancingMode,
    ).toContain('cannot determine');
    expect((validateGceServerGroupCommand(values) as any).loadBalancingPolicy?.balancingMode).toContain(
      'cannot determine',
    );
    expect(wrapper.find('[role="alert"]').text()).toContain('cannot determine');
  });

  it('resolves persisted UTILIZATION to RATE for EXTERNAL_MANAGED mixed with REGIONAL_EXTERNAL_NETWORK', () => {
    const values = command({
      loadBalancers: ['external-managed-lb', 'regional-network-lb'],
      loadBalancingPolicy: {
        balancingMode: 'UTILIZATION',
        capacityScaler: 0.75,
        maxConnectionsPerInstance: 25,
        maxRatePerInstance: 100,
        maxUtilization: 0.7,
        namedPorts: [{ name: 'https', port: 443 }],
        unknownField: 'keep',
      },
      backingData: {
        ...command().backingData,
        filtered: {
          loadBalancerIndex: {
            'external-managed-lb': {
              name: 'external-managed-lb',
              loadBalancerType: 'EXTERNAL_MANAGED',
              listeners: [{ name: 'external-listener' }],
              backendServices: [{ name: 'external-backend', portName: 'https' }],
            },
            'regional-network-lb': {
              name: 'regional-network-lb',
              loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
            },
          },
        },
      },
    });

    expect(transformGceServerGroupCommand(values).loadBalancingPolicy).toEqual({
      balancingMode: 'RATE',
      capacityScaler: 0.75,
      maxRatePerInstance: 100,
      namedPorts: [{ name: 'https', port: 443 }],
      unknownField: 'keep',
    });
  });

  it('resolves the default UTILIZATION policy to RATE for HTTP mixed with REGIONAL_EXTERNAL_NETWORK', () => {
    const values = command({
      loadBalancers: ['http-lb', 'regional-network-lb'],
      backingData: {
        ...command().backingData,
        filtered: {
          loadBalancerIndex: {
            'http-lb': {
              name: 'http-lb',
              loadBalancerType: 'HTTP',
              listeners: [{ name: 'http-listener' }],
              backendServices: [{ name: 'http-backend', portName: 'http' }],
            },
            'regional-network-lb': {
              name: 'regional-network-lb',
              loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
            },
          },
        },
      },
    });

    expect(transformGceServerGroupCommand(values).loadBalancingPolicy).toEqual({
      balancingMode: 'RATE',
      capacityScaler: 1,
      namedPorts: [{ name: 'http', port: 80 }],
    });
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);
    const page = wrapper.instance() as GceServerGroupLoadBalancers;
    expect((page.validate(values) as any).loadBalancingPolicy?.maxRatePerInstance).toContain('Max rate');

    const submitCommand = {
      ...values,
      application: 'fnord',
      capacity: { desired: 3, max: 3, min: 3 },
      image: 'ubuntu',
      zone: 'us-central1-a',
    };
    expect((validateGceServerGroupCommand(submitCommand) as any).loadBalancingPolicy?.maxRatePerInstance).toContain(
      'Max rate',
    );
  });

  it('preserves classic NETWORK balancing modes when mixed with REGIONAL_EXTERNAL_NETWORK', () => {
    const values = command({
      loadBalancers: ['legacy-network-lb', 'regional-network-lb'],
      loadBalancingPolicy: {
        balancingMode: 'CONNECTION',
        capacityScaler: 1,
        maxConnectionsPerInstance: 25,
        namedPorts: [],
      },
      backingData: {
        ...command().backingData,
        filtered: {
          loadBalancerIndex: {
            'legacy-network-lb': {
              name: 'legacy-network-lb',
              loadBalancerType: 'NETWORK',
            },
            'regional-network-lb': {
              name: 'regional-network-lb',
              loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
            },
          },
        },
      },
    });
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);

    expect(selectOptions(wrapper, 'Balancing mode')).toEqual([
      ['CONNECTION', 'CONNECTION'],
      ['UTILIZATION', 'UTILIZATION'],
    ]);
    expect(wrapper.find('input[aria-label="Max connections per instance"]').prop('value')).toBe(25);
  });

  it('does not resurrect persisted balancing modes for REGIONAL_EXTERNAL_NETWORK-only selections', () => {
    const values = regionalExternalNetworkCommand({
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: 1,
        maxRatePerInstance: 50,
        namedPorts: [{ name: 'http', port: 8080 }],
      },
    });
    const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);

    expect(wrapper.find('select[aria-label="Balancing mode"]').exists()).toBe(false);
    expect((wrapper.instance() as GceServerGroupLoadBalancers).validate(values)).toEqual({});
  });

  it('validates finite concrete policy bounds and accepts expressions only in pipeline modes', () => {
    const invalid = command({
      loadBalancers: ['regional-lb'],
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: Number.POSITIVE_INFINITY,
        maxRatePerInstance: Number.NaN,
        namedPorts: [{ name: '', port: 65536 }],
      },
    });
    const { formik } = testProps(invalid);
    const page = shallow(
      <GceServerGroupLoadBalancers app={{} as any} formik={formik} />,
    ).instance() as GceServerGroupLoadBalancers;

    expect(page.validate(invalid)).toEqual({
      loadBalancingPolicy: {
        capacityScaler: 'Capacity must be between 0 and 100%.',
        maxRatePerInstance: 'Max rate must be a finite number greater than or equal to zero.',
        namedPorts: [{ name: 'Port name required.', port: 'Port must be an integer between 1 and 65535.' }],
      },
    });

    const pipeline = command({
      loadBalancers: ['regional-lb'],
      viewState: { mode: 'editPipeline', dirty: {} },
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: '${ parameters.capacity }',
        maxRatePerInstance: '${ parameters.rate }',
        namedPorts: [{ name: 'http', port: '${ parameters.port }' }],
      },
    });
    expect(page.validate(pipeline)).toEqual({});
    expect(page.validate({ ...pipeline, viewState: { mode: 'create', dirty: {} } })).toEqual({
      loadBalancingPolicy: {
        capacityScaler: 'Capacity must be between 0 and 100%.',
        maxRatePerInstance: 'Max rate must be a finite number greater than or equal to zero.',
        namedPorts: [{ port: 'Port must be an integer between 1 and 65535.' }],
      },
    });

    const blank = command({
      loadBalancers: ['regional-lb'],
      loadBalancingPolicy: {
        balancingMode: 'RATE',
        capacityScaler: '',
        maxRatePerInstance: '',
        namedPorts: [],
      },
    });
    expect(page.validate(blank)).toEqual({
      loadBalancingPolicy: {
        capacityScaler: 'Capacity must be between 0 and 100%.',
        maxRatePerInstance: 'Max rate must be a finite number greater than or equal to zero.',
      },
    });
  });

  describe('load balancer metadata attribution', () => {
    it('drops deselected unavailable load balancer names from selection metadata and submission payloads', async () => {
      const values = command({
        loadBalancers: ['unavailable-a', 'unavailable-b'],
        loadBalancerMetadata: {
          'load-balancer-names': ['listener-a', 'listener-b'],
        },
        loadBalancerSelectionMetadata: {
          'unavailable-a': { key: 'load-balancer-names', names: ['listener-a'] },
          'unavailable-b': { key: 'load-balancer-names', names: ['listener-b'] },
        },
      });
      const { adapter, formik } = testProps(values);
      adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
        command: nextCommand,
        result: { dirty: {} },
      }));
      const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

      wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
        target: { selectedOptions: [{ value: 'unavailable-a' }] },
      });
      await flush();

      expect(formik.setValues).toHaveBeenCalledWith(
        jasmine.objectContaining({
          loadBalancerSelectionMetadata: {
            'unavailable-a': { key: 'load-balancer-names', names: ['listener-a'] },
          },
          loadBalancers: ['unavailable-a'],
        }),
      );
      const submitted = transformGceServerGroupCommand(formik.setValues.calls.mostRecent().args[0]);
      expect(submitted.loadBalancers).toEqual(['listener-a']);
      expect(submitted.instanceMetadata).toEqual({
        'load-balancer-names': 'listener-a',
      });
    });

    it('blocks submit when selection changes while unattributed legacy metadata remains', async () => {
      const values = command({
        loadBalancers: ['regional-lb'],
        loadBalancerMetadata: {
          'load-balancer-names': ['orphan-listener', 'regional-lb'],
        },
        viewState: {
          mode: 'create',
          dirty: {},
          initialLoadBalancers: ['regional-lb'],
        },
      });
      const { adapter, formik } = testProps(values);
      adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
        command: nextCommand,
        result: { dirty: {} },
      }));
      const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);

      wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
        target: { selectedOptions: [{ value: 'global-lb' }] },
      });
      await flush();

      const changedCommand = formik.setValues.calls.mostRecent().args[0];
      const errors = (wrapper.instance() as GceServerGroupLoadBalancers).validate(changedCommand);
      expect(errors.loadBalancers).toContain('cannot be mapped to the current selection');
      expect(validateGceServerGroupCommand(changedCommand).loadBalancers).toContain(
        'cannot be mapped to the current selection',
      );
    });

    it('allows unchanged legacy flat metadata without per-selection references', () => {
      const values = command({
        loadBalancers: ['persisted-lb'],
        loadBalancerMetadata: {
          'load-balancer-names': ['persisted-listener'],
        },
      });
      const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={testProps(values).formik} />);

      expect((wrapper.instance() as GceServerGroupLoadBalancers).validate(values).loadBalancers).toBeUndefined();
      expect(validateGceServerGroupCommand(values).loadBalancers).toBeUndefined();
    });

    it('clears validation after changing then reverting to the initial load balancer selection', async () => {
      const values = command({
        loadBalancers: ['regional-lb'],
        loadBalancerMetadata: {
          'load-balancer-names': ['orphan-listener', 'regional-lb'],
        },
        viewState: {
          mode: 'create',
          dirty: {},
          initialLoadBalancers: ['regional-lb'],
        },
      });
      const { adapter, formik } = testProps(values);
      formik.setValues = jasmine.createSpy('setValues').and.callFake((next: IGceServerGroupCommand) => {
        formik.values = next;
      });
      adapter.applyConfigurationUpdate.and.callFake(async (nextCommand) => ({
        command: nextCommand,
        result: { dirty: {} },
      }));
      const wrapper = shallow(<GceServerGroupLoadBalancers app={{} as any} formik={formik} adapter={adapter} />);
      const page = wrapper.instance() as GceServerGroupLoadBalancers;

      wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
        target: { selectedOptions: [{ value: 'global-lb' }] },
      });
      await flush();

      const changedCommand = formik.setValues.calls.mostRecent().args[0];
      expect(page.validate(changedCommand).loadBalancers).toContain('cannot be mapped to the current selection');

      wrapper.find('select[aria-label="Load balancers"]').simulate('change', {
        target: { selectedOptions: [{ value: 'regional-lb' }] },
      });
      await flush();

      const revertedCommand = formik.setValues.calls.mostRecent().args[0];
      expect(page.validate(revertedCommand).loadBalancers).toBeUndefined();
      expect(validateGceServerGroupCommand(revertedCommand).loadBalancers).toBeUndefined();
      expect(revertedCommand.viewState.loadBalancerSelectionsChanged).toBe(false);
    });
  });
});

function selectOptions(wrapper: ReturnType<typeof shallow>, ariaLabel = 'Load balancers'): string[][] {
  return wrapper
    .find(`select[aria-label="${ariaLabel}"] option`)
    .map((option) => [option.prop('value') as string, option.text()]);
}

function expectPolicyError(
  wrapper: ReturnType<typeof shallow>,
  selector: string,
  errorName: string,
  message: string,
): void {
  const id = `gce-load-balancing-policy-${errorName}-error`;
  expect(wrapper.find(selector).prop('aria-invalid')).toBe(true);
  expect(wrapper.find(selector).prop('aria-describedby')).toBe(id);
  expect(wrapper.find(`#${id}[role="alert"]`).text()).toContain(message);
}

function testProps(values = command()) {
  const formik = ({
    values,
    setFieldValue: jasmine.createSpy('setFieldValue'),
    setValues: jasmine.createSpy('setValues'),
  } as unknown) as FormikProps<IGceServerGroupCommand>;
  const adapter = ({
    applyConfigurationRefresh: jasmine
      .createSpy('applyConfigurationRefresh')
      .and.callFake(async (nextCommand: IGceServerGroupCommand) => ({ command: nextCommand, result: { dirty: {} } })),
    applyConfigurationUpdate: jasmine
      .createSpy('applyConfigurationUpdate')
      .and.callFake(async (nextCommand: IGceServerGroupCommand) => ({ command: nextCommand, result: { dirty: {} } })),
  } as unknown) as jasmine.SpyObj<IGceServerGroupWizardAdapter>;
  return { adapter, formik };
}

function regionalExternalNetworkCommand(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return command({
    loadBalancers: ['regional-network-lb'],
    backingData: {
      ...command().backingData,
      filtered: {
        loadBalancerIndex: {
          'regional-network-lb': {
            name: 'regional-network-lb',
            loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
          },
        },
      },
    },
    ...overrides,
  });
}

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    credentials: 'account-a',
    regional: false,
    region: 'us-central1',
    loadBalancers: [],
    backingData: {
      loadBalancers: [
        {
          accounts: [
            {
              name: 'account-a',
              regions: [
                {
                  loadBalancers: [
                    { name: 'regional-lb', region: 'us-central1' },
                    { name: 'global-lb', region: 'global' },
                    { name: 'other-region-lb', region: 'europe-west1' },
                    { name: 'regional-lb', region: 'us-central1' },
                  ],
                },
              ],
            },
            {
              name: 'account-b',
              regions: [{ loadBalancers: [{ name: 'other-account-lb', region: 'us-central1' }] }],
            },
          ],
        },
      ],
      filtered: {
        loadBalancerIndex: {
          'regional-lb': {
            name: 'regional-lb',
            loadBalancerType: 'HTTP',
            listeners: [{ name: 'regional-listener' }],
            backendServices: [
              { name: 'backend-a', portName: 'http' },
              { name: 'backend-b', portName: 'metrics' },
              { name: 'backend-a', portName: 'http' },
            ],
          },
          'global-lb': { name: 'global-lb', loadBalancerType: 'TCP', backendServices: [] },
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
