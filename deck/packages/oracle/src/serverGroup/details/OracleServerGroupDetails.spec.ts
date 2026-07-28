import { ServerGroupReader } from '@spinnaker/core';

import { OracleImageReader } from '../../image/image.reader';
import { oracleServerGroupDetailsGetter } from './OracleServerGroupDetails';

describe('oracleServerGroupDetailsGetter', () => {
  it('auto-closes when server group details cannot be loaded', (done) => {
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(Promise.reject(new Error('not found')) as any);
    const autoClose = jasmine.createSpy('autoClose');

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
      error: done.fail,
    });
  });

  it('falls back to the image id when image lookup fails', (done) => {
    spyOn(ServerGroupReader, 'getServerGroup').and.returnValue(
      Promise.resolve({
        name: 'my-server-group',
        region: 'us-phoenix-1',
        launchConfig: { imageId: 'ocid1.image.oc1..example' },
      }) as any,
    );
    spyOn(OracleImageReader.prototype, 'getImage').and.returnValue(Promise.reject(new Error('image lookup failed')));

    oracleServerGroupDetailsGetter(
      {
        app: { name: 'my-app' },
        serverGroup: { accountId: 'oracle-account', region: 'us-phoenix-1', name: 'my-server-group' },
      },
      jasmine.createSpy('autoClose'),
    ).subscribe((details: any) => {
      expect(details.image).toEqual({ id: 'ocid1.image.oc1..example', name: 'ocid1.image.oc1..example' });
      done();
    }, done.fail);
  });
});
