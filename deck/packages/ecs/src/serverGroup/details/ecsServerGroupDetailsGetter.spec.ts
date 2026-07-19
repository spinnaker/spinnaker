import { ServerGroupReader } from '@spinnaker/core';

import { ecsServerGroupDetailsGetter } from './ecsServerGroupDetailsGetter';
import { EcsServerGroupTransformer } from '../serverGroup.transformer';

function deferred<T>() {
  let resolve: (value: T) => void;
  let reject: (error: any) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve: resolve!, reject: reject! };
}

describe('ecsServerGroupDetailsGetter', () => {
  const serverGroup = { accountId: 'test', name: 'fnord-main-v001', region: 'us-east-1' } as any;

  it('merges the application summary over full details and normalizes the result', (done) => {
    const summary = {
      account: 'test',
      name: 'fnord-main-v001',
      region: 'us-east-1',
      instanceCounts: { total: 2, up: 2 },
      isDisabled: true,
    };
    const app = {
      name: 'fnord',
      ready: () => Promise.resolve(),
      serverGroups: { data: [summary] },
      loadBalancers: { data: [] },
    } as any;
    const details = {
      name: 'fnord-main-v001',
      isDisabled: false,
      taskDefinition: { taskName: 'fnord:1' },
      scalingPolicies: [],
    };
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(Promise.resolve(details) as any);
    const normalize = spyOn(
      EcsServerGroupTransformer.prototype,
      'normalizeServerGroupDetails',
    ).and.callFake((value: any) => ({ ...value, normalized: true }));
    const autoClose = jasmine.createSpy('autoClose');

    ecsServerGroupDetailsGetter({ app, serverGroup } as any, autoClose).subscribe({
      next: (result: any) => {
        expect(normalize).toHaveBeenCalledOnceWith(
          jasmine.objectContaining({
            account: 'test',
            instanceCounts: summary.instanceCounts,
            isDisabled: true,
            taskDefinition: details.taskDefinition,
          }),
        );
        expect(result.normalized).toBe(true);
        expect(autoClose).not.toHaveBeenCalled();
      },
      error: done.fail,
      complete: done,
    });
  });

  it('uses a matching load balancer server group summary when the application summary is unavailable', (done) => {
    const loadBalancerSummary = {
      account: 'test',
      name: 'fnord-main-v001',
      region: 'us-east-1',
      instanceCounts: { total: 1, up: 1 },
    };
    const app = {
      name: 'fnord',
      ready: () => Promise.resolve(),
      serverGroups: { data: [] },
      loadBalancers: { data: [{ account: 'test', region: 'us-east-1', serverGroups: [loadBalancerSummary] }] },
    } as any;
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(
      Promise.resolve({ name: 'fnord-main-v001', scalingPolicies: [] }) as any,
    );

    ecsServerGroupDetailsGetter({ app, serverGroup } as any, done.fail).subscribe({
      next: (result: any) => expect(result.instanceCounts).toBe(loadBalancerSummary.instanceCounts),
      error: done.fail,
      complete: done,
    });
  });

  it('does not emit or auto-close after the details subscription is cancelled', async () => {
    const request = deferred<any>();
    const app = {
      name: 'fnord',
      ready: () => Promise.resolve(),
      serverGroups: { data: [] },
      loadBalancers: { data: [] },
    } as any;
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(request.promise as any);
    const autoClose = jasmine.createSpy('autoClose');
    const next = jasmine.createSpy('next');
    const subscription = ecsServerGroupDetailsGetter({ app, serverGroup } as any, autoClose).subscribe(next);

    await Promise.resolve();
    subscription.unsubscribe();
    request.reject(new Error('not found'));
    await request.promise.catch(() => undefined);
    await Promise.resolve();

    expect(next).not.toHaveBeenCalled();
    expect(autoClose).not.toHaveBeenCalled();
  });
});
