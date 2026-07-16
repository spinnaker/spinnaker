import { shallow } from 'enzyme';
import React from 'react';

import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';
import { Policies } from './Policies';
import { GceAutoHealingPolicyEditor } from '../../../../autoHealingPolicy';
import { GceAutoscalingPolicyEditor } from '../../../../autoscalingPolicy';

describe('GCE server group Policies page', () => {
  it('reuses the policy editors and supplies autohealing health checks from filtered backing data', async () => {
    const values = command({
      enableAutoScaling: true,
      enableAutoHealing: true,
      autoscalingPolicy: { minNumReplicas: 1, maxNumReplicas: 4, unknownPolicyField: 'keep' },
      autoHealingPolicy: { healthCheck: 'check', initialDelaySec: 300, unknownPolicyField: 'keep' },
      backingData: {
        healthChecks: [{ account: 'wrong', name: 'unfiltered' }],
        filtered: {
          healthChecks: [
            {
              displayName: 'check',
              kind: 'healthCheck',
              name: 'check',
              selfLink: 'https://compute/healthChecks/check',
            },
          ],
        },
      },
    });
    const wrapper = shallow(<Policies app={{} as any} formik={formik(values)} />);

    expect(wrapper.find(GceAutoscalingPolicyEditor).prop('policy')).toBe(values.autoscalingPolicy);
    expect(wrapper.find(GceAutoHealingPolicyEditor).prop('policy')).toBe(values.autoHealingPolicy);

    const reader = wrapper.find(GceAutoHealingPolicyEditor).prop('reader') as any;
    expect(await reader.listHealthChecks()).toEqual([
      jasmine.objectContaining({ account: 'account', name: 'check', selfLink: 'https://compute/healthChecks/check' }),
    ]);
  });

  it('round trips autoscaling through fixed capacity using the canonical policy and supported source contract', () => {
    const persistedPolicy = { minNumReplicas: 1, maxNumReplicas: 4, unknownPolicyField: 'keep' };
    const persisted = command({
      enableAutoScaling: true,
      autoscalingPolicy: persistedPolicy,
      capacity: { min: 1, max: 4, desired: 2 },
      source: { region: 'us-central1', serverGroupName: 'app-v001', useSourceCapacity: true },
      viewState: { mode: 'clone', useSimpleCapacity: false, unrelated: 'keep' },
    });
    const persistedFormik = formik(persisted);
    const persistedPage = shallow(<Policies app={{} as any} formik={persistedFormik} />);

    persistedPage.find('[data-testid="enable-autoscaling"]').simulate('change', { target: { checked: false } });

    expect(persisted.enableAutoScaling).toBe(false);
    expect(persisted.autoscalingPolicy).toBeNull();
    expect(persisted.overwriteAncestorAutoscalingPolicy).toBe(true);
    expect(persisted.source).toEqual({
      region: 'us-central1',
      serverGroupName: 'app-v001',
      useSourceCapacity: false,
    });
    expect(persisted.viewState).toEqual({ mode: 'clone', useSimpleCapacity: true, unrelated: 'keep' });
    expect(persisted.capacity).toEqual({ min: 2, max: 2, desired: 2 });

    persistedPage.setProps({ formik: persistedFormik });
    persistedPage.find('[data-testid="enable-autoscaling"]').simulate('change', { target: { checked: true } });

    expect(persisted.enableAutoScaling).toBe(true);
    expect(persisted.overwriteAncestorAutoscalingPolicy).toBe(false);
    expect(persisted.autoscalingPolicy).toEqual({
      minNumReplicas: 2,
      maxNumReplicas: 2,
      coolDownPeriodSec: 60,
      cpuUtilization: { utilizationTarget: 0.5 },
    });
    expect(persisted.source.useSourceCapacity).toBe(false);
    expect(persisted.viewState).toEqual({ mode: 'clone', useSimpleCapacity: false, unrelated: 'keep' });
    expect(persisted.capacity).toEqual({ min: 2, max: 2, desired: 2 });
  });

  (['create', 'createPipeline', 'editPipeline'] as const).forEach((mode) => {
    it(`does not overwrite ancestor autoscaling when disabling in ${mode} mode`, () => {
      const values = command({
        autoscalingPolicy: { minNumReplicas: 1, maxNumReplicas: 4 },
        viewState: { mode },
      });
      const wrapper = shallow(<Policies app={{} as any} formik={formik(values)} />);

      wrapper.find('[data-testid="enable-autoscaling"]').simulate('change', { target: { checked: false } });

      expect(values.overwriteAncestorAutoscalingPolicy).toBe(false);
    });
  });

  it('preserves autohealing policy fields and limits ancestor overwrite to clone disables', () => {
    const policy = { healthCheck: 'check', initialDelaySec: 300, unknownPolicyField: 'keep' };
    const clone = command({ enableAutoHealing: true, autoHealingPolicy: policy, viewState: { mode: 'clone' } });
    const clonePage = shallow(<Policies app={{} as any} formik={formik(clone)} />);

    clonePage.find('[data-testid="enable-autohealing"]').simulate('change', { target: { checked: false } });

    expect(clone.enableAutoHealing).toBe(false);
    expect(clone.overwriteAncestorAutoHealingPolicy).toBe(true);
    expect(clone.autoHealingPolicy).toBe(policy);

    const create = command({ enableAutoHealing: true, autoHealingPolicy: policy, viewState: { mode: 'create' } });
    const createPage = shallow(<Policies app={{} as any} formik={formik(create)} />);
    createPage.find('[data-testid="enable-autohealing"]').simulate('change', { target: { checked: false } });

    expect(create.overwriteAncestorAutoHealingPolicy).toBe(false);
  });

  it('creates valid defaults on first enable and synchronizes compatibility state', () => {
    const values = command({
      enableAutoScaling: false,
      enableAutoHealing: false,
      autoscalingPolicy: undefined,
      autoHealingPolicy: undefined,
      overwriteAncestorAutoHealingPolicy: true,
      capacity: { min: 3, max: 3, desired: 3 },
      source: { useSourceCapacity: true },
      viewState: { mode: 'clone' },
    });
    const wrapper = shallow(<Policies app={{} as any} formik={formik(values)} />);

    wrapper.find('[data-testid="enable-autoscaling"]').simulate('change', { target: { checked: true } });
    wrapper.find('[data-testid="enable-autohealing"]').simulate('change', { target: { checked: true } });

    expect(values.autoscalingPolicy).toEqual({
      minNumReplicas: 3,
      maxNumReplicas: 3,
      coolDownPeriodSec: 60,
      cpuUtilization: { utilizationTarget: 0.5 },
    });
    expect(values.capacity).toEqual({ min: 3, max: 3, desired: 3 });
    expect(values.source.useSourceCapacity).toBe(false);
    expect(values.viewState).toEqual({ mode: 'clone', useSimpleCapacity: false });
    expect(values.autoHealingPolicy).toEqual({ initialDelaySec: 300 });
    expect(values.overwriteAncestorAutoHealingPolicy).toBe(false);
  });

  it('derives autoscaling rendering and validation from canonical policy presence', () => {
    const page = new Policies({ app: {} as any, formik: formik(command()) } as any);
    const canonical = command({
      enableAutoScaling: false,
      autoscalingPolicy: { minNumReplicas: 3, maxNumReplicas: 2, cpuUtilization: {} },
    });
    const canonicalWrapper = shallow(<Policies app={{} as any} formik={formik(canonical)} />);

    expect(canonicalWrapper.find('[data-testid="enable-autoscaling"]').prop('checked')).toBe(true);
    expect(canonicalWrapper.find(GceAutoscalingPolicyEditor).exists()).toBe(true);

    expect(
      page.validate({
        ...canonical,
        enableAutoHealing: true,
        autoHealingPolicy: {
          healthCheckUrl: 'https://compute/healthChecks/web',
          initialDelaySec: -1,
          maxUnavailable: { fixed: 1, percent: 1 },
        },
      }),
    ).toEqual({
      autoscalingPolicy: {
        maxNumReplicas: 'Maximum capacity must be at least the minimum capacity.',
        coolDownPeriodSec: 'Cool-down period must be an integer of at least 15 seconds.',
        metric: 'At least one complete autoscaling metric required.',
      },
      autoHealingPolicy: {
        healthCheck: 'Health check required.',
        healthCheckKind: 'Health check kind required.',
        initialDelaySec: 'Initial delay must be an integer between 0 and 2147483647 seconds.',
        maxUnavailable: 'Max unavailable must contain one valid fixed or percent value.',
      },
    });
    expect(
      page.validate(
        command({
          enableAutoScaling: true,
          autoscalingPolicy: null,
          enableAutoHealing: false,
          autoHealingPolicy: { unknownPolicyField: 'keep' },
        }),
      ),
    ).toEqual({});
  });

  it('synchronizes edited autoscaling policy bounds and prevents source inheritance', () => {
    const values = command({
      enableAutoScaling: false,
      autoscalingPolicy: {
        minNumReplicas: 1,
        maxNumReplicas: 4,
        coolDownPeriodSec: 60,
        cpuUtilization: { utilizationTarget: 0.5 },
      },
      capacity: { min: 1, max: 4, desired: 3 },
      source: { useSourceCapacity: true },
      viewState: { mode: 'editPipeline', useSimpleCapacity: true, unrelated: 'keep' },
    });
    const wrapper = shallow(<Policies app={{} as any} formik={formik(values)} />);
    const policy = {
      minNumReplicas: 2,
      maxNumReplicas: 6,
      coolDownPeriodSec: 90,
      cpuUtilization: { utilizationTarget: 0.7 },
    };

    wrapper.find(GceAutoscalingPolicyEditor).prop('onChange')(policy);

    expect(values.autoscalingPolicy).toBe(policy);
    expect(values.capacity).toEqual({ min: 2, max: 6, desired: 3 });
    expect(values.enableAutoScaling).toBe(true);
    expect(values.source.useSourceCapacity).toBe(false);
    expect(values.viewState).toEqual({ mode: 'editPipeline', useSimpleCapacity: false, unrelated: 'keep' });
  });

  it('requires autoscaling capacity, cooldown, and real metric values', () => {
    const page = new Policies({ app: {} as any, formik: formik(command()) } as any);

    expect(
      page.validate(
        command({
          enableAutoScaling: true,
          autoscalingPolicy: {
            cpuUtilization: { utilizationTarget: 0 },
            loadBalancingUtilization: { utilizationTarget: 1 },
            customMetricUtilizations: [{ metric: 'custom' }],
          },
        }),
      ),
    ).toEqual({
      autoscalingPolicy: {
        minNumReplicas: 'Minimum capacity must be a nonnegative integer.',
        maxNumReplicas: 'Maximum capacity must be a nonnegative integer.',
        coolDownPeriodSec: 'Cool-down period must be an integer of at least 15 seconds.',
        metric: 'At least one complete autoscaling metric required.',
      },
    });
  });

  it('matches autoscaling schedule and scale-in validation from the completed modal', () => {
    const page = new Policies({ app: {} as any, formik: formik(command()) } as any);
    const values = command({
      enableAutoScaling: true,
      autoscalingPolicy: {
        minNumReplicas: 0,
        maxNumReplicas: 2,
        coolDownPeriodSec: 15,
        cpuUtilization: { utilizationTarget: 0.5 },
        scalingSchedules: [{ scheduleName: 'incomplete' }],
        scaleInControl: { maxScaledInReplicas: { percent: 101 }, timeWindowSec: 30 },
      },
    });

    expect(page.validate(values)).toEqual({
      autoscalingPolicy: {
        scalingSchedules: 'Every scaling schedule must be complete and within supported bounds.',
        scaleInControl: 'Scale-in control values are outside supported bounds.',
      },
    });
  });

  it('accepts complete policies at modal boundary values', () => {
    const page = new Policies({ app: {} as any, formik: formik(command()) } as any);

    expect(
      page.validate(
        command({
          enableAutoScaling: true,
          autoscalingPolicy: {
            minNumReplicas: 0,
            maxNumReplicas: 2,
            coolDownPeriodSec: 15,
            cpuUtilization: { utilizationTarget: 0.5 },
            scalingSchedules: [
              {
                scheduleName: 'overnight',
                minimumRequiredInstances: 1,
                scheduleCron: '0 0 * * *',
                timezone: 'Europe/London',
                duration: 301,
              },
            ],
            scaleInControl: { maxScaledInReplicas: { percent: 100 }, timeWindowSec: 60 },
          },
          enableAutoHealing: true,
          autoHealingPolicy: {
            healthCheck: 'web',
            healthCheckKind: 'healthCheck',
            initialDelaySec: 0,
            maxUnavailable: { percent: 100 },
          },
        }),
      ),
    ).toEqual({});
  });
});

function command(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    credentials: 'account',
    regional: false,
    viewState: { mode: 'create' },
    backingData: { filtered: { healthChecks: [] } },
    ...overrides,
  } as IGceServerGroupCommand;
}

function formik(values: IGceServerGroupCommand): any {
  return {
    values,
    setFieldValue: jasmine.createSpy('setFieldValue').and.callFake((field: string, value: any) => {
      values[field] = value;
    }),
  };
}
