import classNames from 'classnames';
import React from 'react';

import { HistoryEventRow } from './HistoryEventRow';
import { ManagedReader } from '../ManagedReader';
import { IManagedResourceSummary } from '../../domain';
import {
  IModalComponentProps,
  ModalBody,
  ModalHeader,
  showModal,
  standardGridTableLayout,
  Table,
  usePollingData,
} from '../../presentation';
import { Spinner } from '../../widgets';

import './ManagedResourceHistoryModal.less';

export type IManagedResourceHistoryModalProps = IModalComponentProps &
  Pick<IManagedResourceSummary, 'id' | 'displayName'>;

const EVENT_POLLING_INTERVAL = 10 * 1000;

export const showManagedResourceHistoryModal = (props: IManagedResourceHistoryModalProps) =>
  showModal(ManagedResourceHistoryModal, props);

const tableLayout = standardGridTableLayout([{ unit: 'px', size: 70 }, 8, 1.5]);

export const ManagedResourceHistoryModal = ({ id, displayName, dismissModal }: IManagedResourceHistoryModalProps) => {
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
      <ModalHeader>Resource history - {displayName}</ModalHeader>
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
                columns={['Level', 'Event', 'Time']}
                expandable={historyEvents?.some(
                  ({ delta, tasks, message, reason, exceptionMessage }) =>
                    delta || tasks || message || reason || exceptionMessage,
                )}
              >
                {(historyEvents || []).map((event) => (
                  <HistoryEventRow key={event.type + event.timestamp} event={event} dismissModal={dismissModal} />
                ))}
              </Table>
            </div>
          )}
        </div>
      </ModalBody>
    </>
  );
};
