import { groupBy } from 'lodash';
import { DateTime } from 'luxon';

import type { IVersionActionsProps } from './ArtifactActionModal';
import { ACTION_DISPLAY_NAMES, getActionStatusData } from './VersionOperation';
import type { VersionAction } from '../../artifactActions/ArtifactActions';
import { useMarkVersionAsBad, useMarkVersionAsGood, usePinVersion, useUnpinVersion } from './hooks';
import { useApplicationContextSafe } from '../../../presentation';
import type { QueryArtifactVersion, QueryConstraint, QueryLifecycleStep } from '../types';
import { timeDiffToString } from '../../../utils';
import type { SingleVersionArtifactVersion } from '../../versionsHistory/types';

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

export const useCreateVersionRollbackActions = (
  props: Omit<IVersionActionsProps, 'application'>,
): VersionAction[] | undefined => {
  const application = useApplicationContextSafe();
  const { isPinned, isVetoed, isCurrent } = props;

  const basePayload: IVersionActionsProps = { application: application.name, ...props };

  const onUnpin = useUnpinVersion(basePayload);

  const onPin = usePinVersion(basePayload);

  const onMarkAsBad = useMarkVersionAsBad(basePayload);

  const onMarkAsGood = useMarkVersionAsGood(basePayload);

  const actions: VersionAction[] = [
    isPinned
      ? {
          content: 'Unpin version...',
          onClick: onUnpin,
        }
      : {
          content: isCurrent ? 'Pin version...' : 'Rollback to here...',
          onClick: onPin,
        },
  ];

  if (isVetoed) {
    actions.push({
      content: 'Allow deploying...',
      onClick: onMarkAsGood,
    });
  } else {
    if (!isCurrent) {
      actions.push({
        content: isCurrent ? 'Rollback...' : 'Reject...',
        onClick: onMarkAsBad,
      });
    }
  }

  return actions;
};

export const isVersionVetoed = (version?: QueryArtifactVersion | SingleVersionArtifactVersion) =>
  version?.status === 'VETOED';

export const isVersionPending = (version?: QueryArtifactVersion | SingleVersionArtifactVersion) =>
  version?.status === 'APPROVED' || version?.status === 'PENDING';
