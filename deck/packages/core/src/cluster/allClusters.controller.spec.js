import { hasReactCloneServerGroupModal } from './allClusters.controller';

describe('AllClustersCtrl provider filters', () => {
  it('only offers providers with React clone server group modals', () => {
    expect(
      hasReactCloneServerGroupModal(null, null, { serverGroup: { CloneServerGroupModal: { show: () => null } } }),
    ).toBe(true);
    expect(hasReactCloneServerGroupModal(null, null, { serverGroup: {} })).toBe(false);
    expect(hasReactCloneServerGroupModal(null, null, {})).toBe(false);
  });
});
