import { ServerGroupReader } from '@spinnaker/core';

import { cloudrunServerGroupDetailsGetter } from './cloudrunServerGroupDetailsGetter';

describe('cloudrunServerGroupDetailsGetter', () => {
  const props: any = {
    app: {
      name: 'fnord',
      serverGroups: { data: [] },
      loadBalancers: { data: [] },
    },
    serverGroup: {
      name: 'service-v000',
      accountId: 'test',
      region: 'us-central1',
    },
  };

  it('auto-closes and completes when server group lookup fails', () =>
    new Promise((done, reject) => {
      const autoClose = vi.fn();
      vi.spyOn(ServerGroupReader, 'getServerGroup').mockReturnValue(Promise.reject(new Error('not found')) as any);

      cloudrunServerGroupDetailsGetter(props, autoClose).subscribe({
        next: () => fail('should not emit a server group'),
        error: () => fail('should not emit an error'),
        complete: () => {
          expect(autoClose).toHaveBeenCalled();
          done();
        },
      });
    }));

  it('auto-closes and completes when lookup returns no server group', () =>
    new Promise((done, reject) => {
      const autoClose = vi.fn();
      vi.spyOn(ServerGroupReader, 'getServerGroup').mockReturnValue(Promise.resolve(null) as any);

      cloudrunServerGroupDetailsGetter(props, autoClose).subscribe({
        next: () => fail('should not emit a server group'),
        error: () => fail('should not emit an error'),
        complete: () => {
          expect(autoClose).toHaveBeenCalled();
          done();
        },
      });
    }));
});
