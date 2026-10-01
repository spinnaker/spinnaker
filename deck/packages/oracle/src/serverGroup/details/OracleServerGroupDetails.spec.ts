import { ServerGroupReader } from '@spinnaker/core';

import { OracleImageReader } from '../../image/image.reader';
import { oracleServerGroupDetailsGetter } from './OracleServerGroupDetails';

describe('oracleServerGroupDetailsGetter', () => {
  it('auto-closes when server group details cannot be loaded', () =>
    new Promise((done, reject) => {
      vi.spyOn(ServerGroupReader, 'getServerGroup').mockReturnValue(Promise.reject(new Error('not found')) as any);
      const autoClose = vi.fn();

      oracleServerGroupDetailsGetter(
        {
          app: { name: 'my-app' },
          serverGroup: { accountId: 'oracle-account', region: 'us-phoenix-1', name: 'my-server-group' },
        },
        autoClose,
      ).subscribe({
        complete: () => {
          expect(autoClose).toHaveBeenCalled();
          done();
        },
        error: reject,
      });
    }));

  it('falls back to the image id when image lookup fails', () =>
    new Promise((done, reject) => {
      vi.spyOn(ServerGroupReader, 'getServerGroup').mockReturnValue(
        Promise.resolve({
          name: 'my-server-group',
          region: 'us-phoenix-1',
          launchConfig: { imageId: 'ocid1.image.oc1..example' },
        }) as any,
      );
      vi.spyOn(OracleImageReader.prototype, 'getImage').mockReturnValue(
        Promise.reject(new Error('image lookup failed')),
      );

      oracleServerGroupDetailsGetter(
        {
          app: { name: 'my-app' },
          serverGroup: { accountId: 'oracle-account', region: 'us-phoenix-1', name: 'my-server-group' },
        },
        vi.fn(),
      ).subscribe((details: any) => {
        expect(details.image).toEqual({ id: 'ocid1.image.oc1..example', name: 'ocid1.image.oc1..example' });
        done();
      }, reject);
    }));
});
