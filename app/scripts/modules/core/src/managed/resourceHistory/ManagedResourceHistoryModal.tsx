import classNames from 'classnames';
import React from 'react';

import { IManagedResourceSummary } from 'core/domain';
import { Spinner } from 'core/widgets';

import { HistoryEventRow } from './HistoryEventRow';
import { ManagedReader } from '../ManagedReader';
import {
  IModalComponentProps,
  ModalBody,
  ModalHeader,
  showModal,
  standardGridTableLayout,
  Table,
  usePollingData,
} from '../../presentation';
import { viewConfigurationByEventType } from './utils';

import './ManagedResourceHistoryModal.less';

export interface IManagedResourceHistoryModalProps extends IModalComponentProps {
  resourceSummary: IManagedResourceSummary;
}

const EVENT_POLLING_INTERVAL = 10 * 1000;

export const showManagedResourceHistoryModal = (props: IManagedResourceHistoryModalProps) =>
  showModal(ManagedResourceHistoryModal, props);

const tableLayout = standardGridTableLayout([4, 2, 2.6]);

export const ManagedResourceHistoryModal = ({ resourceSummary, dismissModal }: IManagedResourceHistoryModalProps) => {
  const { id } = resourceSummary;

  const { status: historyEventStatus, result: historyEvents, refresh } = usePollingData(
    () => ManagedReader.getResourceHistory(id),
    null,
    EVENT_POLLING_INTERVAL,
    [],
  );

  const isLoading = !historyEvents && ['NONE', 'PENDING'].includes(historyEventStatus);
  const shouldShowExistingData = !isLoading && historyEventStatus !== 'REJECTED';

  return (
    <>
      <ModalHeader>Resource history</ModalHeader>
      <ModalBody>
        <div
          className={classNames('ManagedResourceHistoryModal', {
            'flex-container-h middle center flex-grow': !shouldShowExistingData,
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
                expandable={historyEvents?.some(
                  ({ delta, tasks, message, reason, exceptionMessage }) =>
                    delta || tasks || message || reason || exceptionMessage,
                )}
              >
                {(historyEvents || [])
                  .filter(({ type }) => viewConfigurationByEventType[type])
                  .map((event) => (
                    <HistoryEventRow key={event.type + event.timestamp} {...{ event, resourceSummary, dismissModal }} />
                  ))}
              </Table>
            </div>
          )}
        </div>
      </ModalBody>
    </>
  );
};
