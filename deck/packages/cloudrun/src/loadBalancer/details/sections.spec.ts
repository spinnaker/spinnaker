import { compareCloudrunLoadBalancerServerGroups } from './sections';

describe('compareCloudrunLoadBalancerServerGroups', () => {
  it('sorts enabled server groups first and names descending within each state', () => {
    const sorted = [
      { name: 'alpha', isDisabled: true },
      { name: 'zulu', isDisabled: false },
      { name: 'alpha', isDisabled: false },
      { name: 'zulu', isDisabled: true },
    ].sort(compareCloudrunLoadBalancerServerGroups as any);

    expect(sorted.map((serverGroup) => `${serverGroup.isDisabled}:${serverGroup.name}`)).toEqual([
      'false:zulu',
      'false:alpha',
      'true:zulu',
      'true:alpha',
    ]);
  });
});
