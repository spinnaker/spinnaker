import { UISref } from '@uirouter/react';
import classNames from 'classnames';
import isEqual from 'lodash/isEqual';
import { DateTime } from 'luxon';
import React from 'react';

import { ManagedResourceDiffTable } from './ManagedResourceDiffTable';
import { RelativeTimestamp } from '../RelativeTimestamp';
import { IManagedResourceDiff, IManagedResourceEvent } from '../../domain';
import { TableCell, TableRow } from '../../presentation';

type LogLevel = IManagedResourceEvent['level'];

const eventLevelToClass: { [key in LogLevel]?: string } = {
  WARNING: 'event-warning',
  ERROR: 'event-error',
  SUCCESS: 'event-success',
};

const ExpandedRowContent: React.FC<{
  level: LogLevel;
  diff?: IManagedResourceDiff;
  tasks?: Array<{ id: string; name: string }>;
  message?: string;
  dismissModal?: () => any;
}> = ({ level, diff, tasks, dismissModal, message }) => {
  return (
    <div className="flex-container-v left">
      {message && <div className={classNames('sp-padding-xs-yaxis', eventLevelToClass[level])}>{message}</div>}
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
  dismissModal?: () => void;
}

export const HistoryEventRow: React.FC<HistoryEventRowProps> = React.memo(({ event, dismissModal }) => {
  const { timestamp, delta, tasks, message, reason, exceptionMessage, level } = event;
  const eventDate = React.useMemo(() => DateTime.fromISO(timestamp), [timestamp]);

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
        <span className={classNames(eventLevelToClass[level] || 'event-info', 'event-level')}>{event.level}</span>
      </TableCell>
      <TableCell>
        <span className="event-type">{event.displayName}</span>
      </TableCell>
      <TableCell>
        <div className="flex-container-h middle wrap">
          <RelativeTimestamp timestamp={eventDate} clickToCopy />
        </div>
      </TableCell>
    </TableRow>
  );
}, isEqual);
