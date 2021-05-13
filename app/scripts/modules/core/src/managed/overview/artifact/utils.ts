import { groupBy } from 'lodash';
import { DateTime } from 'luxon';

import { showModal, useApplicationContext } from 'core/presentation';
import { timeDiffToString } from 'core/utils';

import { MarkAsBadActionModal, PinActionModal, UnpinActionModal } from './ArtifactActionModal';
import { ManagedWriter } from '../../ManagedWriter';
import { VersionAction } from './VersionMetadata';
import { ACTION_DISPLAY_NAMES, getActionStatusData } from './VersionOperation';
import { useFetchApplicationLazyQuery } from '../../graphql/graphql-sdk';
import { QueryArtifactVersion, QueryConstraint, QueryLifecycleStep } from '../types';
import { OVERVIEW_VERSION_STATUSES } from '../utils';
import { MODAL_MAX_WIDTH } from '../../utils/defaults';

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

export const getLifecycleEventDuration = (
  version: QueryArtifactVersion | undefined,
  type: QueryLifecycleStep['type'],
) => {
  const event = version?.lifecycleSteps?.find((step) => step.type === type);
  if (!event) return undefined;
  const { startedAt, completedAt } = event;
  if (startedAt && completedAt) {
    return timeDiffToString(DateTime.fromISO(startedAt), DateTime.fromISO(completedAt));
  }
  return undefined;
};

export const getLifecycleEventLink = (version: QueryArtifactVersion | undefined, type: QueryLifecycleStep['type']) => {
  return version?.lifecycleSteps?.find((step) => step.type === type)?.link;
};

interface ICreateVersionActionsProps {
  environment: string;
  reference: string;
  version: string;
  buildNumber?: string;
  commitMessage?: string;
  isPinned: boolean;
  compareLinks?: {
    previous?: string;
    current?: string;
  };
}

export const useCreateVersionActions = ({
  environment,
  reference,
  version,
  buildNumber,
  commitMessage,
  isPinned,
  compareLinks,
}: ICreateVersionActionsProps): VersionAction[] | undefined => {
  const application = useApplicationContext();
  if (!application) throw new Error('Application context is empty');
  const [refetch] = useFetchApplicationLazyQuery({
    variables: { appName: application.name, statuses: OVERVIEW_VERSION_STATUSES },
    fetchPolicy: 'network-only',
  });

  const onUnpin = () => {
    showModal(
      UnpinActionModal,
      {
        application: application.name,
        environment,
        title: [`Unpin #${buildNumber}`, commitMessage].filter(Boolean).join(' - '),
        actionName: 'Unpin',
        onAction: () =>
          ManagedWriter.unpinArtifactVersion({
            application: application.name,
            environment,
            reference,
          }),
        onSuccess: refetch,
        withComment: false,
      },
      { maxWidth: MODAL_MAX_WIDTH },
    );
  };

  const onPin = () => {
    showModal(
      PinActionModal,
      {
        application: application.name,
        title: [`Pin #${buildNumber}`, commitMessage].filter(Boolean).join(' - '),
        actionName: 'Pin',
        onAction: (comment: string) =>
          ManagedWriter.pinArtifactVersion({
            application: application.name,
            environment,
            reference,
            comment,
            version,
          }),
        onSuccess: refetch,
      },
      { maxWidth: MODAL_MAX_WIDTH },
    );
  };

  const onMarkAsBad = () => {
    if (!application) throw new Error('Application context is empty');
    showModal(
      MarkAsBadActionModal,
      {
        application: application.name,
        title: [`Mark as Bad #${buildNumber}`, commitMessage].filter(Boolean).join(' - '),
        actionName: 'Mark as Bad',
        onAction: (comment: string) =>
          ManagedWriter.markArtifactVersionAsBad({
            application: application.name,
            environment,
            reference,
            comment,
            version,
          }),
        onSuccess: refetch,
      },
      { maxWidth: MODAL_MAX_WIDTH },
    );
  };

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

    {
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

  return actions.length ? actions : undefined;
};
