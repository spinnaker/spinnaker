import type { IAmazonServerGroup } from '../../../domain';

export interface IAmazonRollbackSelection {
  allServerGroups: IAmazonServerGroup[];
  previousServerGroup?: IAmazonServerGroup;
  serverGroup: IAmazonServerGroup;
}

function applicationFor(serverGroup: IAmazonServerGroup): string | undefined {
  return serverGroup.app || serverGroup.moniker?.app;
}

function inRollbackScope(
  applicationName: string,
  selectedServerGroup: IAmazonServerGroup,
  candidate: IAmazonServerGroup,
): boolean {
  return (
    applicationFor(candidate) === applicationName &&
    candidate.cluster === selectedServerGroup.cluster &&
    candidate.account === selectedServerGroup.account &&
    candidate.region === selectedServerGroup.region
  );
}

export function getAmazonRollbackCandidates(
  applicationName: string,
  selectedServerGroup: IAmazonServerGroup,
  allServerGroups: IAmazonServerGroup[],
): IAmazonServerGroup[] {
  return allServerGroups
    .filter(
      (candidate) =>
        candidate.name !== selectedServerGroup.name && inRollbackScope(applicationName, selectedServerGroup, candidate),
    )
    .sort((a, b) => b.name.localeCompare(a.name));
}

export function isAmazonRollbackAvailable(
  applicationName: string,
  selectedServerGroup: IAmazonServerGroup,
  allServerGroups: IAmazonServerGroup[],
): boolean {
  return (
    !selectedServerGroup.isDisabled ||
    getAmazonRollbackCandidates(applicationName, selectedServerGroup, allServerGroups).some(
      (candidate) => !candidate.isDisabled,
    )
  );
}

export function selectAmazonRollbackServerGroups(
  applicationName: string,
  selectedServerGroup: IAmazonServerGroup,
  allServerGroups: IAmazonServerGroup[],
): IAmazonRollbackSelection | undefined {
  let serverGroup = selectedServerGroup;
  let previousServerGroup: IAmazonServerGroup | undefined;

  if (selectedServerGroup.isDisabled) {
    previousServerGroup = selectedServerGroup;
    serverGroup = getAmazonRollbackCandidates(applicationName, selectedServerGroup, allServerGroups)
      .filter((candidate) => !candidate.isDisabled)
      .sort(
        (a, b) =>
          (b.instanceCounts?.total || 0) - (a.instanceCounts?.total || 0) ||
          (b.createdTime || 0) - (a.createdTime || 0),
      )[0];

    if (!serverGroup) {
      return undefined;
    }
  }

  const candidates = getAmazonRollbackCandidates(applicationName, serverGroup, allServerGroups);
  if (candidates.length === 1 && !previousServerGroup) {
    previousServerGroup = candidates[0];
  }

  return { allServerGroups: candidates, previousServerGroup, serverGroup };
}
