import { TaskExecutor } from '@spinnaker/core';

import { GceAutoscalingPolicyWriter } from './GceAutoscalingPolicyWriter';

describe('GceAutoscalingPolicyWriter', () => {
  const application = { name: 'my-app' } as any;
  const regionalServerGroup = {
    account: 'my-account',
    name: 'my-app-main-v001',
    region: 'us-central1',
    type: 'gce',
  } as any;
  const policy = { minNumReplicas: 0, maxNumReplicas: 5 } as any;

  beforeEach(() => {
    spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({}) as any);
  });

  it('upserts an autoscaling policy with the GCE operation contract', () => {
    GceAutoscalingPolicyWriter.upsertAutoscalingPolicy(application, regionalServerGroup, policy);

    expect(TaskExecutor.executeTask).toHaveBeenCalledWith({
      application,
      description: 'Upsert scaling policy my-app-main-v001',
      job: [
        {
          type: 'upsertScalingPolicy',
          cloudProvider: 'gce',
          credentials: 'my-account',
          region: 'us-central1',
          serverGroupName: 'my-app-main-v001',
          autoscalingPolicy: policy,
        },
      ],
    });
  });

  it('merges optional reason and health providers into the autoscaling upsert job', () => {
    GceAutoscalingPolicyWriter.upsertAutoscalingPolicy(application, regionalServerGroup, policy, {
      interestingHealthProviderNames: ['Google'],
      reason: 'raise autoscaling ceiling',
    });

    expect(TaskExecutor.executeTask).toHaveBeenCalledWith({
      application,
      description: 'Upsert scaling policy my-app-main-v001',
      job: [
        {
          type: 'upsertScalingPolicy',
          cloudProvider: 'gce',
          credentials: 'my-account',
          region: 'us-central1',
          serverGroupName: 'my-app-main-v001',
          autoscalingPolicy: policy,
          interestingHealthProviderNames: ['Google'],
          reason: 'raise autoscaling ceiling',
        },
      ],
    });
  });

  it('uses a zonal server group location as the operation region fallback', () => {
    GceAutoscalingPolicyWriter.deleteAutoscalingPolicy(application, {
      ...regionalServerGroup,
      region: undefined,
      zone: 'us-central1-a',
    });

    expect(TaskExecutor.executeTask).toHaveBeenCalledWith({
      application,
      description: 'Delete scaling policy my-app-main-v001',
      job: [
        {
          type: 'deleteScalingPolicy',
          cloudProvider: 'gce',
          credentials: 'my-account',
          region: 'us-central1-a',
          serverGroupName: 'my-app-main-v001',
        },
      ],
    });
  });

  it('upserts auto-healing through the scaling policy operation', () => {
    const autoHealingPolicy = { healthCheck: 'web-health-check', initialDelaySec: 0 } as any;

    GceAutoscalingPolicyWriter.upsertAutoHealingPolicy(application, regionalServerGroup, autoHealingPolicy);

    expect(TaskExecutor.executeTask).toHaveBeenCalledWith({
      application,
      description: 'Upsert autohealing policy my-app-main-v001',
      job: [
        {
          type: 'upsertScalingPolicy',
          cloudProvider: 'gce',
          credentials: 'my-account',
          region: 'us-central1',
          serverGroupName: 'my-app-main-v001',
          autoHealingPolicy,
        },
      ],
    });
  });

  it('marks only the auto-healing policy for deletion', () => {
    GceAutoscalingPolicyWriter.deleteAutoHealingPolicy(application, regionalServerGroup);

    expect(TaskExecutor.executeTask).toHaveBeenCalledWith({
      application,
      description: 'Delete autohealing policy my-app-main-v001',
      job: [
        {
          type: 'deleteScalingPolicy',
          cloudProvider: 'gce',
          credentials: 'my-account',
          region: 'us-central1',
          serverGroupName: 'my-app-main-v001',
          deleteAutoHealingPolicy: true,
        },
      ],
    });
  });
});
