import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import type { IGceServerGroupCommand } from '../GceServerGroupWizard.types';
import { Policies, validateGceServerGroupPolicies } from './Policies';

vi.mock('../../../../autoscalingPolicy', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../../autoscalingPolicy')>();
  return {
    ...actual,
    GceAutoscalingPolicyEditor: ({ onChange, policy }: any) => (
      <div>
        <output aria-label="Autoscaling policy">{JSON.stringify(policy)}</output>
        <button
          onClick={() =>
            onChange({
              minNumReplicas: 2,
              maxNumReplicas: 6,
              coolDownPeriodSec: 90,
              cpuUtilization: { utilizationTarget: 0.7 },
            })
          }
          type="button"
        >
          Edit autoscaling policy
        </button>
      </div>
    ),
  };
});

vi.mock('../../../../autoHealingPolicy', () => ({
  GceAutoHealingPolicyEditor: ({ policy, reader }: any) => {
    const [healthChecks, setHealthChecks] = React.useState<any[]>([]);
    return (
      <div>
        <output aria-label="Autohealing policy">{JSON.stringify(policy)}</output>
        <button onClick={() => reader.listHealthChecks().then(setHealthChecks)} type="button">
          Load health checks
        </button>
        <output aria-label="Health checks">{JSON.stringify(healthChecks)}</output>
      </div>
    );
  },
}));

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
    renderPolicies(values);

    expect(screen.getByLabelText('Autoscaling policy')).toHaveTextContent('unknownPolicyField');
    expect(screen.getByLabelText('Autohealing policy')).toHaveTextContent('unknownPolicyField');
    fireEvent.click(screen.getByRole('button', { name: 'Load health checks' }));
    await waitFor(() =>
      expect(screen.getByLabelText('Health checks')).toHaveTextContent('https://compute/healthChecks/check'),
    );
    expect(JSON.parse(screen.getByLabelText('Health checks').textContent || '[]')).toEqual([
      expect.objectContaining({ account: 'account', name: 'check', selfLink: 'https://compute/healthChecks/check' }),
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
    const page = renderPolicies(persisted);

    fireEvent.click(screen.getByTestId('enable-autoscaling'));

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

    page.rerenderPage(persisted);
    fireEvent.click(screen.getByTestId('enable-autoscaling'));

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
      renderPolicies(values);

      fireEvent.click(screen.getByTestId('enable-autoscaling'));

      expect(values.overwriteAncestorAutoscalingPolicy).toBe(false);
    });
  });

  it('preserves autohealing policy fields and limits ancestor overwrite to clone disables', () => {
    const policy = { healthCheck: 'check', initialDelaySec: 300, unknownPolicyField: 'keep' };
    const clone = command({ enableAutoHealing: true, autoHealingPolicy: policy, viewState: { mode: 'clone' } });
    const page = renderPolicies(clone);

    fireEvent.click(screen.getByTestId('enable-autohealing'));

    expect(clone.enableAutoHealing).toBe(false);
    expect(clone.overwriteAncestorAutoHealingPolicy).toBe(true);
    expect(clone.autoHealingPolicy).toBe(policy);

    const create = command({ enableAutoHealing: true, autoHealingPolicy: policy, viewState: { mode: 'create' } });
    page.unmount();
    renderPolicies(create);
    fireEvent.click(screen.getByTestId('enable-autohealing'));

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
    const page = renderPolicies(values);

    fireEvent.click(screen.getByTestId('enable-autoscaling'));
    page.rerenderPage(values);
    fireEvent.click(screen.getByTestId('enable-autohealing'));

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
    const canonical = command({
      enableAutoScaling: false,
      autoscalingPolicy: { minNumReplicas: 3, maxNumReplicas: 2, cpuUtilization: {} },
    });
    renderPolicies(canonical);

    expect(screen.getByTestId('enable-autoscaling')).toBeChecked();
    expect(screen.getByLabelText('Autoscaling policy')).toBeInTheDocument();

    expect(
      validateGceServerGroupPolicies({
        ...canonical,
        enableAutoHealing: true,
        autoHealingPolicy: {
          healthCheckUrl: 'https://compute/healthChecks/web',
          initialDelaySec: -1,
          maxUnavailable: { fixed: 1, percent: 1 },
        } as any,
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
      },
    });
    expect(
      validateGceServerGroupPolicies(
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
    renderPolicies(values);

    fireEvent.click(screen.getByRole('button', { name: 'Edit autoscaling policy' }));

    expect(values.autoscalingPolicy).toEqual({
      minNumReplicas: 2,
      maxNumReplicas: 6,
      coolDownPeriodSec: 90,
      cpuUtilization: { utilizationTarget: 0.7 },
    });
    expect(values.capacity).toEqual({ min: 2, max: 6, desired: 3 });
    expect(values.enableAutoScaling).toBe(true);
    expect(values.source.useSourceCapacity).toBe(false);
    expect(values.viewState).toEqual({ mode: 'editPipeline', useSimpleCapacity: false, unrelated: 'keep' });
  });

  it('requires autoscaling capacity, cooldown, and real metric values', () => {
    expect(
      validateGceServerGroupPolicies(
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

    expect(validateGceServerGroupPolicies(values)).toEqual({
      autoscalingPolicy: {
        scalingSchedules: 'Every scaling schedule must be complete and within supported bounds.',
        scaleInControl: 'Scale-in control values are outside supported bounds.',
      },
    });
  });

  it('accepts complete policies at modal boundary values', () => {
    expect(
      validateGceServerGroupPolicies(
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
          } as any,
        }),
      ),
    ).toEqual({});
  });
});

function renderPolicies(values: IGceServerGroupCommand) {
  const pageProps = { app: {} as any, formik: formik(values) };
  const rendered = render(<Policies {...pageProps} />);
  return {
    ...rendered,
    rerenderPage(nextValues: IGceServerGroupCommand) {
      rendered.rerender(<Policies {...pageProps} formik={formik(nextValues)} />);
    },
  };
}

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
    setFieldValue: vi.fn().mockImplementation((field: string, value: any) => {
      values[field] = value;
    }),
  };
}
