import { KubernetesV2ServerGroupTransformer } from './serverGroupTransformer.service';
import { Application } from '@spinnaker/core';
import { IKubernetesServerGroup } from '../interfaces';

describe('KubernetesV2ServerGroupTransformer', function () {
  it('normalizes server group name', async () => {
    const transformer = new KubernetesV2ServerGroupTransformer();
    const ACCOUNT = 'myaccount';
    const LOCATION = 'myns';
    const SG_MANAGER = 'sgmanager';
    const KIND = 'daemonSet';

    const sgMgrRef = { account: ACCOUNT, location: LOCATION, name: SG_MANAGER };
    const sgAccount = ({
      account: ACCOUNT,
      name: 'myservergroup',
      region: LOCATION,
      serverGroupManagers: [sgMgrRef],
    } as unknown) as IKubernetesServerGroup;

    const data = [{ name: `${KIND} ${SG_MANAGER}`, region: LOCATION, account: ACCOUNT, kind: KIND }];
    const app = new Application('test', null, []);
    app.getDataSource = () => {
      return {
        ready: () => Promise.resolve(data),
        data,
      } as any;
    };

    const sg = await transformer.normalizeServerGroup(sgAccount, app);
    expect(sg).not.toBe(null);
    expect(sg.serverGroupManagers.length).toBe(1);
    expect(sgMgrRef.name).toBe('daemonSet sgmanager');
  });

  it('does not normalize server group if different account', async () => {
    const transformer = new KubernetesV2ServerGroupTransformer();
    const ACCOUNT = 'myaccount';
    const ACCOUNT2 = 'account2';
    const LOCATION = 'myns';
    const SG_MANAGER = 'sgmanager';
    const KIND = 'daemonSet';

    const sgMgrRef = { account: ACCOUNT, location: LOCATION, name: SG_MANAGER };
    const sgAccount: IKubernetesServerGroup = ({
      account: ACCOUNT,
      name: 'myservergroup',
      region: LOCATION,
      serverGroupManagers: [sgMgrRef],
    } as unknown) as IKubernetesServerGroup;

    const data = [{ name: `${KIND} ${SG_MANAGER}`, region: LOCATION, account: ACCOUNT2, kind: KIND }];
    const app = new Application('test', null, []);
    app.getDataSource = () => {
      return {
        ready: () => Promise.resolve(data),
        data,
      } as any;
    };

    const sg = await transformer.normalizeServerGroup(sgAccount, app);
    expect(sg).not.toBe(null);
    expect(sg.serverGroupManagers.length).toBe(1);
    expect(sgMgrRef.name).toBe('sgmanager');
  });
});
