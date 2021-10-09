import { groupBy } from 'lodash';
import { DateTime } from 'luxon';

import { ACTION_DISPLAY_NAMES, getActionStatusData } from './VersionOperation';
import type { FetchCurrentVersionQuery } from '../../graphql/graphql-sdk';
import type { QueryArtifactVersion, QueryConstraint, QueryLifecycleStep } from '../types';
import { timeDiffToString } from '../../../utils';
import type { HistoryArtifactVersionExtended, SingleVersionArtifactVersion } from '../../versionsHistory/types';

export const getConstraintsStatusSummary = (constraints: QueryConstraint[]) => {
  let finalStatus: QueryConstraint['status'] = 'PASS';
  for (const { status } of constraints) {
    if (status === 'FAIL') {
      finalStatus = 'FAIL';
      break;
    } else if (status === 'PENDING' || status === 'BLOCKED') {
      finalStatus = 'PENDING';
    } else if (status === 'FORCE_PASS' && finalStatus !== 'PENDING') {
      finalStatus = 'FORCE_PASS';
    }
  }

  const byStatus = groupBy(constraints, (c) => getActionStatusData(c.status)?.displayName || 'pending');
  const summary = ACTION_DISPLAY_NAMES.map((displayName) => {
    const constraintsOfStatus = byStatus[displayName];
    return constraintsOfStatus ? `${constraintsOfStatus.length} ${displayName}` : undefined;
  })
    .filter(Boolean)
    .join(', ');

  return { text: summary, status: finalStatus };
};

export const getLifecycleEventByType = (
  version: QueryArtifactVersion | undefined,
  type: QueryLifecycleStep['type'],
): QueryLifecycleStep | undefined => {
  return version?.lifecycleSteps?.find((step) => step.type === type);
};

export const getLifecycleEventDuration = (
  version: QueryArtifactVersion | undefined,
  type: QueryLifecycleStep['type'],
) => {
  const event = getLifecycleEventByType(version, type);
  if (!event) return undefined;
  const { startedAt, completedAt } = event;
  if (startedAt && completedAt) {
    return timeDiffToString(DateTime.fromISO(startedAt), DateTime.fromISO(completedAt));
  }
  return undefined;
};

export const getLifecycleEventLink = (version: QueryArtifactVersion | undefined, type: QueryLifecycleStep['type']) => {
  return getLifecycleEventByType(version, type)?.link;
};

export const isBaking = (version: QueryArtifactVersion) => {
  return getLifecycleEventByType(version, 'BAKE')?.status === 'RUNNING';
};

export interface LifecycleEventSummary {
  startedAt?: DateTime;
  duration?: string;
  link?: string;
  isRunning: boolean;
}

export const getLifecycleEventSummary = (
  version: QueryArtifactVersion | undefined,
  type: QueryLifecycleStep['type'],
): LifecycleEventSummary | undefined => {
  const event = getLifecycleEventByType(version, type);
  if (!event) return undefined;
  return {
    startedAt: event.startedAt ? DateTime.fromISO(event.startedAt) : undefined,
    duration: getLifecycleEventDuration(version, type),
    isRunning: event.status === 'RUNNING',
    link: event.link,
  };
};

export const isVersionVetoed = (version?: QueryArtifactVersion | SingleVersionArtifactVersion) =>
  version?.status === 'VETOED';

export const isVersionPending = (version?: QueryArtifactVersion | SingleVersionArtifactVersion) =>
  version?.status === 'APPROVED' || version?.status === 'PENDING';

export type ICurrentVersion = NonNullable<
  NonNullable<
    NonNullable<FetchCurrentVersionQuery['application']>['environments'][number]['state']['artifacts']
  >[number]['versions']
>[number];

export type IVersionRelativeAgeToCurrent = 'CURRENT' | 'NEWER' | 'OLDER';

export const getRelativeAgeToCurrent = ({
  isCurrent,
  createdAt,
  currentVersion,
}: {
  isCurrent?: boolean;
  createdAt?: string;
  currentVersion?: ICurrentVersion;
}): IVersionRelativeAgeToCurrent => {
  if (isCurrent) return 'CURRENT';
  if (
    !createdAt ||
    !currentVersion?.createdAt ||
    new Date(createdAt).getTime() < new Date(currentVersion.createdAt).getTime()
  )
    return 'OLDER';
  return 'NEWER';
};

export interface IVersionDetails {
  buildNumber?: string;
  commitMessage?: string;
  commitSha?: string;
  createdAt?: string;
}

export const extractVersionRollbackDetails = (
  version: QueryArtifactVersion | HistoryArtifactVersionExtended | ICurrentVersion,
): IVersionDetails => {
  return {
    buildNumber: version.buildNumber,
    commitMessage: version.gitMetadata?.commitInfo?.message,
    commitSha: version.gitMetadata?.commit,
    createdAt: version.createdAt,
  };
};
