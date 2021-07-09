import { groupBy } from 'lodash';
import { DateTime } from 'luxon';

import { ACTION_DISPLAY_NAMES, getActionStatusData } from './VersionOperation';
import { MdArtifactStatusInEnvironment } from '../../graphql/graphql-sdk';
import { useMarkVersionAsBad, useMarkVersionAsGood, usePinVersion, useUnpinVersion } from './hooks';
import { useApplicationContextSafe } from '../../../presentation';
import { QueryArtifactVersion, QueryConstraint, QueryLifecycleStep } from '../types';
import { timeDiffToString } from '../../../utils';
import { copyTextToClipboard } from '../../../utils/clipboard/copyTextToClipboard';
import { getIsDebugMode } from '../../utils/debugMode';
import { VersionAction } from '../../versionMetadata/MetadataComponents';

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

interface ICreateVersionActionsProps {
  environment: string;
  reference: string;
  version: string;
  buildNumber?: string;
  commitMessage?: string;
  isPinned: boolean;
  status?: MdArtifactStatusInEnvironment;
  compareLinks?: {
    previous?: string;
    current?: string;
  };
}

export const useCreateVersionActions = ({
  environment,
  reference,
  version,
  status,
  buildNumber,
  commitMessage,
  isPinned,
  compareLinks,
}: ICreateVersionActionsProps): VersionAction[] | undefined => {
  const application = useApplicationContextSafe();

  const basePayload = { application: application.name, environment, reference, version };

  const onUnpin = useUnpinVersion(basePayload, [`Unpin #${buildNumber}`, commitMessage].filter(Boolean).join(' - '));
  const onPin = usePinVersion(basePayload, [`Pin #${buildNumber}`, commitMessage].filter(Boolean).join(' - '));

  const onMarkAsBad = useMarkVersionAsBad(
    basePayload,
    [`Mark #${buildNumber} as Bad`, commitMessage].filter(Boolean).join(' - '),
  );

  const onMarkAsGood = useMarkVersionAsGood(
    basePayload,
    [`Mark #${buildNumber} as Good`, commitMessage].filter(Boolean).join(' - '),
  );

  const actions: VersionAction[] = [
    isPinned
      ? {
          content: 'Unpin version',
          onClick: onUnpin,
        }
      : {
          content: 'Pin version',
          onClick: onPin,
        },
    status === 'VETOED'
      ? {
          content: 'Mark as good',
          onClick: onMarkAsGood,
        }
      : {
          content: 'Mark as bad',
          onClick: onMarkAsBad,
        },
  ];
  if (compareLinks?.current) {
    actions.push({ content: 'Compare to current version', href: compareLinks.current });
  }
  if (compareLinks?.previous) {
    actions.push({ content: 'Compare to previous version', href: compareLinks.previous });
  }

  if (getIsDebugMode()) {
    actions.push({
      content: 'Copy artifact version [Debug]',
      onClick: () => {
        copyTextToClipboard(version);
      },
    });
  }

  return actions.length ? actions : undefined;
};
