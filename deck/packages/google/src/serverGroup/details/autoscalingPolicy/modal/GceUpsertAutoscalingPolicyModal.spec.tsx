import { GceAutoscalingPolicyWriter } from '../../../../autoscalingPolicy';
import { GcePredictiveMethod } from '../../../../autoscalingPolicy';
import { GCEProviderSettings } from '../../../../gce.settings';
import { GceUpsertAutoscalingPolicyModal } from './GceUpsertAutoscalingPolicyModal';

describe('GceUpsertAutoscalingPolicyModal', () => {
  const application = { name: 'my-app' } as any;
  const serverGroup = { account: 'my-account', name: 'my-app-main-v001', region: 'us-central1' } as any;
  const validPolicy = {
    minNumReplicas: 0,
    maxNumReplicas: 5,
    coolDownPeriodSec: 60,
    cpuUtilization: { utilizationTarget: 0.5 },
  };

  function isValid(policy: any): boolean {
    const modal = new GceUpsertAutoscalingPolicyModal({ application, serverGroup, policy } as any);
    modal.state.policy = policy;
    return (modal as any).isValid();
  }

  async function flush(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it('retains edited policy state when task creation is rejected', async () => {
    const rejection = { failureMessage: 'No permission' };
    spyOn(GceAutoscalingPolicyWriter, 'upsertAutoscalingPolicy').and.returnValue(Promise.reject(rejection));
    const modal = new GceUpsertAutoscalingPolicyModal({
      application: { name: 'my-app' },
      serverGroup: { account: 'my-account', name: 'my-app-main-v001', region: 'us-central1' },
      policy: { minNumReplicas: 0, cpuUtilization: { utilizationTarget: 0.5 } },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
    } as any);
    const editedPolicy = { minNumReplicas: 0, cpuUtilization: { utilizationTarget: 0 } };
    modal.state.policy = editedPolicy;

    (modal as any).submit();
    await flush();

    expect(modal.state.policy).toBe(editedPolicy);
    expect(modal.state.taskMonitor.error).toBe(true);
  });

  it('does not count empty metric objects as a configured metric', () => {
    expect(
      isValid({
        ...validPolicy,
        cpuUtilization: {},
        loadBalancingUtilization: {},
        customMetricUtilizations: [{}],
      }),
    ).toBe(false);
  });

  it('requires CPU utilization to be strictly between zero and one', () => {
    expect(isValid({ ...validPolicy, cpuUtilization: { utilizationTarget: 0 } })).toBe(false);
    expect(isValid({ ...validPolicy, cpuUtilization: { utilizationTarget: 1 } })).toBe(false);
    expect(isValid({ ...validPolicy, cpuUtilization: { utilizationTarget: 0.999 } })).toBe(true);
  });

  it('requires custom utilization targets to be greater than zero', () => {
    const customPolicy = {
      ...validPolicy,
      cpuUtilization: {},
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'TIME_SERIES_PER_INSTANCE',
          utilizationTargetType: 'GAUGE',
        },
      ],
    };

    expect(
      isValid({
        ...customPolicy,
        customMetricUtilizations: [{ ...customPolicy.customMetricUtilizations[0], utilizationTarget: 0 }],
      }),
    ).toBe(false);
    expect(
      isValid({
        ...customPolicy,
        customMetricUtilizations: [{ ...customPolicy.customMetricUtilizations[0], utilizationTarget: 5 }],
      }),
    ).toBe(true);
  });

  it('allows zero for group single-instance assignment', () => {
    expect(
      isValid({
        ...validPolicy,
        cpuUtilization: {},
        customMetricUtilizations: [
          {
            metric: 'custom.googleapis.com/queue',
            metricExportScope: 'SINGLE_TIME_SERIES_PER_GROUP',
            scalingpolicy: 'SINGLE_INSTANCE_ASSIGNMENT',
            singleInstanceAssignment: 0,
          },
        ],
      }),
    ).toBe(true);
  });

  it('requires custom metric fields appropriate to per-instance and group scopes', () => {
    const missingPerInstanceType = {
      ...validPolicy,
      cpuUtilization: {},
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'TIME_SERIES_PER_INSTANCE',
          utilizationTarget: 1,
        },
      ],
    };
    const missingGroupAssignment = {
      ...validPolicy,
      cpuUtilization: {},
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'SINGLE_TIME_SERIES_PER_GROUP',
          scalingpolicy: 'SINGLE_INSTANCE_ASSIGNMENT',
        },
      ],
    };
    const missingGroupTarget = {
      ...validPolicy,
      cpuUtilization: {},
      customMetricUtilizations: [
        {
          metric: 'custom.googleapis.com/queue',
          metricExportScope: 'SINGLE_TIME_SERIES_PER_GROUP',
          scalingpolicy: 'UTILIZATION_TARGET',
          utilizationTargetType: 'GAUGE',
        },
      ],
    };

    expect(isValid(missingPerInstanceType)).toBe(false);
    expect(isValid(missingGroupAssignment)).toBe(false);
    expect(isValid(missingGroupTarget)).toBe(false);
  });

  it('requires complete scaling schedules within supported numeric bounds', () => {
    expect(isValid({ ...validPolicy, scalingSchedules: [{ scheduleName: 'overnight' }] })).toBe(false);
    expect(
      isValid({
        ...validPolicy,
        scalingSchedules: [
          {
            scheduleName: 'overnight',
            minimumRequiredInstances: -1,
            scheduleCron: '0 0 * * *',
            timezone: 'Europe/London',
            duration: 300,
          },
        ],
      }),
    ).toBe(false);
    expect(
      isValid({
        ...validPolicy,
        scalingSchedules: [
          {
            scheduleName: 'overnight',
            minimumRequiredInstances: 1,
            scheduleCron: '0 0 * * *',
            timezone: 'Europe/London',
            duration: 1209601,
          },
        ],
      }),
    ).toBe(false);
  });

  it('rejects invalid finite and nonnegative policy bounds', () => {
    expect(isValid({ ...validPolicy, minNumReplicas: -1 })).toBe(false);
    expect(isValid({ ...validPolicy, maxNumReplicas: Number.POSITIVE_INFINITY })).toBe(false);
    expect(isValid({ ...validPolicy, coolDownPeriodSec: Number.NaN })).toBe(false);
  });

  it('requires predictive autoscaling to be enabled and backed by a CPU target', () => {
    const originalFeatureValue = GCEProviderSettings.feature.predictiveAutoscaling;
    GCEProviderSettings.feature.predictiveAutoscaling = false;
    expect(
      isValid({
        ...validPolicy,
        cpuUtilization: { utilizationTarget: 0.5, predictiveMethod: GcePredictiveMethod.STANDARD },
      }),
    ).toBe(false);

    GCEProviderSettings.feature.predictiveAutoscaling = true;
    expect(
      isValid({
        ...validPolicy,
        cpuUtilization: { predictiveMethod: GcePredictiveMethod.STANDARD },
      }),
    ).toBe(false);
    GCEProviderSettings.feature.predictiveAutoscaling = originalFeatureValue;
  });

  it('requires scale-in controls to have one bounded maximum and a bounded time window', () => {
    expect(
      isValid({
        ...validPolicy,
        scaleInControl: { maxScaledInReplicas: { percent: 101 }, timeWindowSec: 60 },
      }),
    ).toBe(false);
    expect(
      isValid({
        ...validPolicy,
        scaleInControl: { maxScaledInReplicas: { fixed: Number.NaN }, timeWindowSec: 60 },
      }),
    ).toBe(false);
    expect(
      isValid({
        ...validPolicy,
        scaleInControl: { maxScaledInReplicas: { fixed: 1 }, timeWindowSec: 3601 },
      }),
    ).toBe(false);
  });
});
