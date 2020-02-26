import React from 'react';
import classNames from 'classnames';
import { DateTime } from 'luxon';
import { UISref } from '@uirouter/react';

import {
  ModalHeader,
  ModalBody,
  Table,
  TableRow,
  TableCell,
  standardGridTableLayout,
  useData,
} from 'core/presentation';

import { relativeTime, timestamp } from 'core/utils';
import { IManagedResourceSummary, IManagedResourceDiff } from 'core/domain';
import { AccountTag } from 'core/account';
import { ManagedReader } from 'core/managed';
import { Spinner } from 'core/widgets';

import { ManagedResourceDiffTable } from './ManagedResourceDiffTable';

import './ManagedResourceHistoryModal.less';

export interface IManagedResourceHistoryModalProps {
  resourceSummary: IManagedResourceSummary;
}

const { useMemo } = React;

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
    displayName: 'Drift detected',
    iconClass: 'icon-md-delta-detected',
    level: 'info',
  },
  ResourceDeltaResolved: {
    displayName: 'Drift resolved',
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
    displayName: 'Error',
    // Needs it's own icon, but could likely be same as ResourceCheckError
    iconClass: 'icon-md-error',
    level: 'error',
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

const renderExpandedRowContent = (
  level: 'info' | 'warning' | 'error',
  diff: IManagedResourceDiff,
  tasks: Array<{ id: string; name: string }>,
  message: string,
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
              <a className="sp-padding-xs-yaxis">{name}</a>
            </UISref>
          ))}
        </div>
      )}
      {diff && <ManagedResourceDiffTable diff={diff} />}
    </div>
  );
};

export const ManagedResourceHistoryModal = ({ resourceSummary }: IManagedResourceHistoryModalProps) => {
  const {
    id,
    moniker: { app, stack, detail },
    locations: { account },
  } = resourceSummary;

  const tableLayout = useMemo(() => standardGridTableLayout([4, 2, 2.6]), []);
  const { status: historyEventStatus, result: historyEvents, refresh } = useData(
    () => ManagedReader.getResourceHistory(id),
    [],
    [],
  );

  const resourceDisplayName = [app, stack, detail].filter(Boolean).join('-');
  const isLoading = ['NONE', 'PENDING'].includes(historyEventStatus);

  return (
    <>
      <ModalHeader>Resource History</ModalHeader>
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
          {historyEventStatus === 'RESOLVED' && (
            <div className="sp-margin-xl-bottom">
              <Table
                layout={tableLayout}
                columns={['Where', 'What', 'When']}
                expandable={historyEvents.some(
                  ({ delta, tasks, message, reason }) => delta || tasks || message || reason,
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
                            ))
                        }
                      >
                        <TableCell>
                          <AccountTag account={account} />{' '}
                          <span className="sp-margin-s-left">{resourceDisplayName}</span>
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
