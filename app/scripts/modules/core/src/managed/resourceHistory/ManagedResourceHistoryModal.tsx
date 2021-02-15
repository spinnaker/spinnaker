import React, { useMemo } from 'react';
import classNames from 'classnames';
import { DateTime } from 'luxon';
import { UISref } from '@uirouter/react';

import {
  IModalComponentProps,
  ModalHeader,
  ModalBody,
  Table,
  TableRow,
  TableCell,
  standardGridTableLayout,
  usePollingData,
  usePrevious,
  showModal,
} from '../../presentation';

import { relativeTime, timestamp } from 'core/utils';
import { IManagedResourceSummary, IManagedResourceDiff, IManagedResourceEvent } from 'core/domain';
import { AccountTag } from 'core/account';
import { ManagedReader } from '../ManagedReader';
import { Spinner } from 'core/widgets';

import { ManagedResourceDiffTable } from './ManagedResourceDiffTable';

import './ManagedResourceHistoryModal.less';

export interface IManagedResourceHistoryModalProps extends IModalComponentProps {
  resourceSummary: IManagedResourceSummary;
}

const EVENT_POLLING_INTERVAL = 10 * 1000;

const viewConfigurationByEventType = {
  ResourceCreated: {
    displayName: 'Created',
    iconClass: 'icon-md-created',
    level: 'info',
  },
  ResourceUpdated: {
    displayName: 'Config updated',
    // Needs it's own icon
    iconClass: 'icon-md-created',
    level: 'info',
  },
  ResourceDeleted: {
    displayName: 'Deleted',
    // Needs it's own icon
    iconClass: 'icon-md-missing-resource',
    level: 'info',
  },
  ResourceMissing: {
    displayName: 'Missing',
    iconClass: 'icon-md-missing-resource',
    level: 'info',
  },
  ResourceValid: {
    displayName: 'Valid',
    // Needs it's own icon
    iconClass: 'icon-md-delta-resolved',
    level: 'info',
  },
  ResourceDeltaDetected: {
    displayName: 'Difference detected',
    iconClass: 'icon-md-delta-detected',
    level: 'info',
  },
  ResourceDeltaResolved: {
    displayName: 'Difference resolved',
    iconClass: 'icon-md-delta-resolved',
    level: 'info',
  },
  ResourceActuationLaunched: {
    displayName: 'Task launched',
    iconClass: 'icon-md-actuation-launched',
    level: 'info',
  },
  ResourceCheckError: {
    displayName: 'Error',
    // Needs it's own icon
    iconClass: 'icon-md-error',
    level: 'error',
  },
  ResourceCheckUnresolvable: {
    displayName: 'Temporary issue',
    // Needs it's own icon, but could likely be same as ResourceCheckError
    iconClass: 'icon-md-error',
    level: 'warning',
  },
  ResourceActuationPaused: {
    displayName: 'Management paused',
    // Needs it's own icon
    iconClass: 'icon-md-paused',
    level: 'warning',
  },
  ResourceActuationResumed: {
    displayName: 'Management resumed',
    // Needs it's own icon
    iconClass: 'icon-md-resumed',
    level: 'info',
  },
} as const;

const mergeNewEvents = (next: IManagedResourceEvent[], previous: IManagedResourceEvent[]) => {
  // Because re-rendering the entire table can be expensive (especially if rows are expanded),
  // we want to try hard to maintain reference equality for events we've already rendered
  // so we can leverage memoization later.
  if (!previous) {
    return next;
  }

  // Let's grab the newest event out of our existing data
  // and try to find it in the new data we just receieved.
  const newestExistingEvent = previous[0];
  const newestExistingEventIndex = next.findIndex(
    ({ type, timestamp }) => type === newestExistingEvent.type && timestamp === newestExistingEvent.timestamp,
  );

  if (newestExistingEventIndex === 0) {
    // The newest event from our previous fetch is still the newest,
    // so we can keep all the previous objects and discard the new ones.
    return previous;
  } else if (newestExistingEventIndex === -1) {
    // For some reason the newest event from the previous fetch isn't in our new data.
    // Either so many events were published that none of the old ones made it in,
    // or something unexpected happened. Either way let's bail out
    // of our optimizations and return fresh data.
    return next;
  }

  // We've got some new events, let's keep the objects from the previous
  // fetch but prepend the new ones.
  const newEvents = next.slice(0, newestExistingEventIndex);
  const combinedEvents: IManagedResourceEvent[] = newEvents.length ? [...newEvents, ...previous] : previous;

  return combinedEvents;
};

const renderExpandedRowContent = (
  level: 'info' | 'warning' | 'error',
  diff: IManagedResourceDiff,
  tasks: Array<{ id: string; name: string }>,
  message: string,
  dismissModal: () => any,
) => {
  return (
    <div className="flex-container-v left">
      {message && (
        <div
          className={classNames('sp-padding-xs-yaxis', {
            'event-warning-message': level === 'warning',
            'event-error-message': level === 'error',
          })}
        >
          {message}
        </div>
      )}
      {tasks && (
        <div className="flex-container-v">
          {tasks.map(({ id, name }) => (
            <UISref key={id} to="home.applications.application.tasks.taskDetails" params={{ taskId: id }}>
              <a className="sp-padding-xs-yaxis" onClick={() => dismissModal()}>
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

export const showManagedResourceHistoryModal = (props: IManagedResourceHistoryModalProps) =>
  showModal(ManagedResourceHistoryModal, props);

export const ManagedResourceHistoryModal = ({ resourceSummary, dismissModal }: IManagedResourceHistoryModalProps) => {
  const {
    id,
    locations: { account },
  } = resourceSummary;

  const tableLayout = useMemo(() => standardGridTableLayout([4, 2, 2.6]), []);

  const { status: historyEventStatus, result: historyEvents, refresh } = usePollingData(
    () => ManagedReader.getResourceHistory(id).then((events) => mergeNewEvents(events, previousHistoryEvents)),
    null,
    EVENT_POLLING_INTERVAL,
    [],
  );
  const previousHistoryEvents: IManagedResourceEvent[] = usePrevious(historyEvents);

  const isLoading = !historyEvents && ['NONE', 'PENDING'].includes(historyEventStatus);
  const shouldShowExistingData = !isLoading && historyEventStatus !== 'REJECTED';

  return (
    <>
      <ModalHeader>Resource history</ModalHeader>
      <ModalBody>
        <div
          className={classNames('ManagedResourceHistoryModal', {
            'flex-container-h middle center flex-grow': isLoading || historyEventStatus === 'REJECTED',
          })}
        >
          {isLoading && <Spinner size="medium" />}
          {historyEventStatus === 'REJECTED' && (
            <div className="flex-container-v middle center">
              <div className="loading-error-message text-semibold sp-margin-m-bottom">Something went wrong.</div>
              <button className="btn btn-default" onClick={refresh}>
                <i className="fa fa-xs fa-sync-alt sp-margin-xs-right" /> Try again
              </button>
            </div>
          )}
          {shouldShowExistingData && (
            <div className="sp-margin-xl-bottom">
              <Table
                layout={tableLayout}
                columns={['Where', 'What', 'When']}
                expandable={historyEvents.some(
                  ({ delta, tasks, message, reason, exceptionMessage }) =>
                    delta || tasks || message || reason || exceptionMessage,
                )}
              >
                {historyEvents
                  .filter(({ type }) => viewConfigurationByEventType[type])
                  .map(({ type, timestamp: eventTimestamp, delta, tasks, message, reason, exceptionMessage }) => {
                    const eventTimestampMillis = DateTime.fromISO(eventTimestamp).toMillis();
                    const hasDetails = delta || tasks || message || reason || exceptionMessage;
                    return (
                      <TableRow
                        key={type + eventTimestamp}
                        renderExpandedContent={
                          hasDetails &&
                          (() =>
                            renderExpandedRowContent(
                              viewConfigurationByEventType[type].level,
                              delta,
                              tasks,
                              message || reason || exceptionMessage,
                              dismissModal,
                            ))
                        }
                      >
                        <TableCell>
                          <AccountTag account={account} />{' '}
                          <span className="sp-margin-s-left">{resourceSummary.displayName}</span>
                        </TableCell>
                        <TableCell>
                          <i
                            className={classNames(
                              'event-type-icon ico ico--withLabel sp-margin-s-right',
                              viewConfigurationByEventType[type].iconClass,
                            )}
                          />{' '}
                          <span className="text-semibold event-type">
                            {viewConfigurationByEventType[type].displayName}
                          </span>
                        </TableCell>
                        <TableCell>
                          <div className="flex-container-h middle wrap">
                            <span className="sp-margin-s-right">{timestamp(eventTimestampMillis)}</span>{' '}
                            <span className="text-italic">{relativeTime(eventTimestampMillis)}</span>
                          </div>
                        </TableCell>
                      </TableRow>
                    );
                  })}
              </Table>
            </div>
          )}
        </div>
      </ModalBody>
    </>
  );
};
