import { UISref } from '@uirouter/react';
import classNames from 'classnames';
import isEqual from 'lodash/isEqual';
import { DateTime } from 'luxon';
import React from 'react';

import { AccountTag } from 'core/account';
import { IManagedResourceDiff, IManagedResourceEvent, IManagedResourceSummary } from 'core/domain';
import { relativeTime, timestamp } from 'core/utils';

import { ManagedResourceDiffTable } from './ManagedResourceDiffTable';
import { TableCell, TableRow } from '../../presentation';

const ExpandedRowContent: React.FC<{
  level: IManagedResourceEvent['level'];
  diff?: IManagedResourceDiff;
  tasks?: Array<{ id: string; name: string }>;
  message?: string;
  dismissModal?: () => any;
}> = ({ level, diff, tasks, dismissModal, message }) => {
  return (
    <div className="flex-container-v left">
      {message && (
        <div
          className={classNames('sp-padding-xs-yaxis', {
            'event-warning-message': level === 'WARNING',
            'event-error-message': level === 'ERROR',
          })}
        >
          {message}
        </div>
      )}
      {tasks && (
        <div className="flex-container-v">
          {tasks.map(({ id, name }) => (
            <UISref key={id} to="home.applications.application.tasks.taskDetails" params={{ taskId: id }}>
              <a className="sp-padding-xs-yaxis" onClick={() => dismissModal?.()}>
                {name}
              </a>
            </UISref>
          ))}
        </div>
      )}
      {diff && <ManagedResourceDiffTable diff={diff} />}
    </div>
  );
};

interface HistoryEventRowProps {
  event: IManagedResourceEvent;
  resourceSummary: IManagedResourceSummary;
  dismissModal?: () => void;
}

export const HistoryEventRow: React.FC<HistoryEventRowProps> = React.memo(
  ({ event, dismissModal, resourceSummary }) => {
    const { timestamp: eventTimestamp, delta, tasks, message, reason, exceptionMessage } = event;
    const eventTimestampMillis = DateTime.fromISO(eventTimestamp).toMillis();
    const hasDetails = delta || tasks || message || reason || exceptionMessage;
    return (
      <TableRow
        renderExpandedContent={
          hasDetails
            ? () => (
                <ExpandedRowContent
                  level={event.level}
                  diff={delta}
                  tasks={tasks}
                  message={message || reason || exceptionMessage}
                  dismissModal={dismissModal}
                />
              )
            : undefined
        }
      >
        <TableCell>
          <AccountTag account={resourceSummary.locations.account} />{' '}
          <span className="sp-margin-s-left">{resourceSummary.displayName}</span>
        </TableCell>
        <TableCell>
          <span className="text-semibold event-type">{event.displayName}</span>
        </TableCell>
        <TableCell>
          <div className="flex-container-h middle wrap">
            <span className="sp-margin-s-right">{timestamp(eventTimestampMillis)}</span>{' '}
            <span className="text-italic">{relativeTime(eventTimestampMillis)}</span>
          </div>
        </TableCell>
      </TableRow>
    );
  },
  isEqual,
);
