import type { IAmazonServerGroup } from '../../../domain';

import { getAmazonRollbackCandidates, isAmazonRollbackAvailable, selectAmazonRollbackServerGroups } from './index';

describe('Amazon rollback selection', () => {
  const applicationName = 'fnord';

  function serverGroup(name: string, overrides: Partial<IAmazonServerGroup> = {}): IAmazonServerGroup {
    return {
      account: 'test',
      app: applicationName,
      cluster: 'fnord-main',
      createdTime: Number(name.match(/\d+$/)?.[0] || 0),
      instanceCounts: { total: 1 },
      isDisabled: false,
      name,
      region: 'eu-west-1',
      ...overrides,
    } as IAmazonServerGroup;
  }

  it('keeps candidates in the same application, cluster, account, and region', () => {
    const selected = serverGroup('fnord-main-v004');
    const eligible = serverGroup('fnord-main-v003', { isDisabled: true });
    const candidates = [
      selected,
      eligible,
      serverGroup('other-app-main-v003', { app: 'other' }),
      serverGroup('fnord-other-v003', { cluster: 'fnord-other' }),
      serverGroup('fnord-main-v003-other-account', { account: 'other' }),
      serverGroup('fnord-main-v003-other-region', { region: 'us-east-1' }),
    ];

    expect(getAmazonRollbackCandidates(applicationName, selected, candidates)).toEqual([eligible]);
  });

  it('uses moniker application identity and sorts candidates newest first', () => {
    const selected = serverGroup('fnord-main-v004');
    const candidates = [
      serverGroup('fnord-main-v001', { app: undefined, moniker: { app: applicationName } as any }),
      serverGroup('fnord-main-v003'),
      serverGroup('fnord-main-v002'),
    ];

    expect(getAmazonRollbackCandidates(applicationName, selected, candidates).map(({ name }) => name)).toEqual([
      'fnord-main-v003',
      'fnord-main-v002',
      'fnord-main-v001',
    ]);
  });

  it('defaults the only other server group as the explicit restore target', () => {
    const selected = serverGroup('fnord-main-v004');
    const previous = serverGroup('fnord-main-v003', { isDisabled: true });

    expect(selectAmazonRollbackServerGroups(applicationName, selected, [selected, previous])).toEqual({
      allServerGroups: [previous],
      previousServerGroup: previous,
      serverGroup: selected,
    });
  });

  it('treats a disabled selection as the restore target and rolls back the largest newest enabled group', () => {
    const selected = serverGroup('fnord-main-v001', { isDisabled: true });
    const olderLarge = serverGroup('fnord-main-v003', {
      createdTime: 100,
      instanceCounts: { total: 5 } as any,
    });
    const newerLarge = serverGroup('fnord-main-v004', {
      createdTime: 200,
      instanceCounts: { total: 5 } as any,
    });
    const small = serverGroup('fnord-main-v002', {
      createdTime: 300,
      instanceCounts: { total: 2 } as any,
    });

    expect(
      selectAmazonRollbackServerGroups(applicationName, selected, [selected, olderLarge, newerLarge, small]),
    ).toEqual({
      allServerGroups: [olderLarge, small, selected],
      previousServerGroup: selected,
      serverGroup: newerLarge,
    });
  });

  it('allows enabled groups and requires an enabled source for disabled groups', () => {
    const enabled = serverGroup('fnord-main-v004');
    const disabled = serverGroup('fnord-main-v003', { isDisabled: true });

    expect(isAmazonRollbackAvailable(applicationName, enabled, [enabled])).toBe(true);
    expect(isAmazonRollbackAvailable(applicationName, disabled, [disabled])).toBe(false);
    expect(isAmazonRollbackAvailable(applicationName, disabled, [disabled, enabled])).toBe(true);
    expect(
      isAmazonRollbackAvailable(applicationName, disabled, [
        disabled,
        serverGroup('fnord-main-v004', { app: 'other' }),
      ]),
    ).toBe(false);
  });
});
