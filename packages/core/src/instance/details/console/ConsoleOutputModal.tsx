import * as React from 'react';

import { IInstanceMultiOutputLog, InstanceReader } from '../../InstanceReader';
import { IInstance } from '../../../domain';
import { IModalComponentProps, ModalBody, ModalFooter, ModalHeader, useData } from '../../../presentation';
import { Spinner } from '../../../widgets';

import './ConsoleOutputModal.less';

export interface IConsoleOutputModalProps extends IModalComponentProps {
  instance: IInstance;
  usesMultiOutput: boolean;
}

export const ConsoleOutputModal = ({ dismissModal, instance, usesMultiOutput }: IConsoleOutputModalProps) => {
  const logContainerEnd = React.useRef(null);

  const getConsoleOutput = () => {
    return InstanceReader.getConsoleOutput(instance.account, instance.region, instance.id, instance.provider);
  };
  const { result: consoleOutputResponse, status, error, refresh: refreshLogs } = useData(getConsoleOutput, null, []);

  const consoleOutput = consoleOutputResponse?.output;
  const [selectedLog, setSelectedLog] = React.useState<IInstanceMultiOutputLog>(
    (consoleOutputResponse?.output || [])[0] as IInstanceMultiOutputLog,
  );
  const loading = status === 'PENDING';

  const jumpToEnd = () => {
    logContainerEnd.current.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <>
      <ModalHeader>Console Output: {instance.id}</ModalHeader>
      <ModalBody>
        <div id="console-output">
          <div className="row">
            <div className="col-md-12">
              {loading && (
                <div className="horizontal center middle spinner-container">
                  <Spinner size="small" />
                </div>
              )}
              {usesMultiOutput && !loading && Boolean(consoleOutput?.length) && (
                <div>
                  {((consoleOutput as IInstanceMultiOutputLog[]) || []).map((log: IInstanceMultiOutputLog) => (
                    <ul className="tabs-basic console-output-tabs">
                      <li
                        className={`console-output-tab ${log?.name === selectedLog?.name ? 'selected' : ''}`}
                        onClick={() => setSelectedLog(log)}
                        ng-repeat="log in vm.consoleOutput"
                      >
                        {log.name}
                      </li>
                    </ul>
                  ))}
                  {selectedLog && <pre className="body-small">{selectedLog?.output}</pre>}
                </div>
              )}
              {!usesMultiOutput && !loading && Boolean(consoleOutput?.length) && (
                <div>
                  <pre className="body-small">{consoleOutput}</pre>
                </div>
              )}
              {error && (
                <div>
                  <p>An error occurred trying to load console output. Please try again later.</p>
                  <p>Exception:</p>
                  <pre>{error.data.body}</pre>
                </div>
              )}
            </div>
          </div>
          <div ref={logContainerEnd} />
        </div>
      </ModalBody>
      <ModalFooter
        primaryActions={
          <>
            {consoleOutput && (
              <button className="btn btn-link" onClick={refreshLogs}>
                Refresh
              </button>
            )}
            {consoleOutput && (
              <button className="btn btn-link" onClick={jumpToEnd}>
                Scroll to End
              </button>
            )}
            <button className="btn btn-primary" onClick={dismissModal}>
              Close
            </button>
          </>
        }
      />
    </>
  );
};
