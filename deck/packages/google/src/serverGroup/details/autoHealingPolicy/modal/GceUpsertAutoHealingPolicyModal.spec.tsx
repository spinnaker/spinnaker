import { SubmitButton } from '@spinnaker/core';

import { GceAutoscalingPolicyWriter } from '../../../../autoscalingPolicy';
import { IGceHealthCheckKind } from '../../../../domain';
import { GceUpsertAutoHealingPolicyModal } from './GceUpsertAutoHealingPolicyModal';

describe('GceUpsertAutoHealingPolicyModal', () => {
  const application = { name: 'my-app' } as any;
  const serverGroup = { account: 'my-account', name: 'my-app-main-v001', region: 'us-central1' } as any;

  function isDisabled(policy: any): boolean {
    const modal = new GceUpsertAutoHealingPolicyModal({ application, serverGroup, policy } as any);
    modal.state.policy = policy;
    const modalContents = (modal.render() as any).props.children;
    const footer = modalContents.find((child: any) => child?.props?.className === 'modal-footer');
    const submitButton = footer.props.children.find((child: any) => child?.type === SubmitButton);
    return submitButton.props.isDisabled;
  }

  async function flush(): Promise<void> {
    await Promise.resolve();
    await Promise.resolve();
  }

  it('retains edited policy state when task creation is rejected', async () => {
    spyOn(GceAutoscalingPolicyWriter, 'upsertAutoHealingPolicy').and.returnValue(
      Promise.reject({ failureMessage: 'No permission' }),
    );
    const modal = new GceUpsertAutoHealingPolicyModal({
      application: { name: 'my-app' },
      serverGroup: { account: 'my-account', name: 'my-app-main-v001', region: 'us-central1' },
      policy: { healthCheck: 'web', initialDelaySec: 300 },
      closeModal: jasmine.createSpy('closeModal'),
      dismissModal: jasmine.createSpy('dismissModal'),
    } as any);
    const editedPolicy = { healthCheck: 'web', initialDelaySec: 0 };
    modal.state.policy = editedPolicy;

    (modal as any).submit();
    await flush();

    expect(modal.state.policy).toBe(editedPolicy);
    expect(modal.state.taskMonitor.error).toBe(true);
  });

  it('requires a health check name and kind pair', () => {
    expect(isDisabled({ healthCheck: 'web', initialDelaySec: 0 })).toBe(true);
    expect(
      isDisabled({
        healthCheck: 'web',
        healthCheckKind: IGceHealthCheckKind.healthCheck,
        initialDelaySec: 0,
      }),
    ).toBe(false);
  });

  it('requires a finite nonnegative initial delay within the backend range', () => {
    const policy = { healthCheck: 'web', healthCheckKind: IGceHealthCheckKind.healthCheck };

    expect(isDisabled({ ...policy, initialDelaySec: -1 })).toBe(true);
    expect(isDisabled({ ...policy, initialDelaySec: Number.POSITIVE_INFINITY })).toBe(true);
    expect(isDisabled({ ...policy, initialDelaySec: 2147483648 })).toBe(true);
  });

  it('does not validate legacy max unavailable data retained in permissive input', () => {
    const policy = {
      healthCheck: 'web',
      healthCheckKind: IGceHealthCheckKind.healthCheck,
      initialDelaySec: 0,
    };

    expect(isDisabled({ ...policy, maxUnavailable: { percent: 101 } })).toBe(false);
    expect(isDisabled({ ...policy, maxUnavailable: { fixed: -1, percent: 1 } })).toBe(false);
  });

  it('does not hydrate legacy max unavailable data into editable modal state', () => {
    const modal = new GceUpsertAutoHealingPolicyModal({
      application,
      serverGroup,
      policy: {
        healthCheck: 'web',
        healthCheckKind: IGceHealthCheckKind.healthCheck,
        initialDelaySec: 0,
        maxUnavailable: { fixed: 2 },
      } as any,
    } as any);

    expect(modal.state.policy).toEqual({
      healthCheck: 'web',
      healthCheckKind: IGceHealthCheckKind.healthCheck,
      initialDelaySec: 0,
    });
  });
});
