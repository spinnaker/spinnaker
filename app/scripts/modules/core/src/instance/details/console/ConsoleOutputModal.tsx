import * as React from 'react';

import { IInstance } from '../../../domain';
import { IModalComponentProps, ModalBody, ModalFooter, ModalHeader } from '../../../presentation';
import { Spinner } from '../../../widgets';
import { InstanceReader, IInstanceConsoleOutput, IInstanceMultiOutputLog } from '../../InstanceReader';

import './ConsoleOutputModal.less';

export interface IConsoleOutputModalProps extends IModalComponentProps {
  instance: IInstance;
  usesMultiOutput: boolean;
}

export const ConsoleOutputModal = ({ dismissModal, instance, usesMultiOutput }: IConsoleOutputModalProps) => {
  const [loading, setLoading] = React.useState(true);
  const [selectedLog, setSelectedLog] = React.useState(null);
  const [consoleOutput, setConsoleOutput] = React.useState(null);
  const [exception, setException] = React.useState(null);
  const logContainerEnd = React.useRef(null);

  const fetchLogs = () => {
    setLoading(true);
    InstanceReader.getConsoleOutput(instance.account, instance.region, instance.id, instance.provider).then(
      (response: IInstanceConsoleOutput) => {
        setConsoleOutput(response.output);
        if (!selectedLog) {
          setSelectedLog(response.output[0]);
        }
        setLoading(false);
      },
      (exception) => setException(exception),
    );
  };

  const jumpToEnd = () => {
    logContainerEnd.current.scrollIntoView({ behavior: 'smooth' });
  };

  React.useEffect(() => {
    fetchLogs();
  }, []);

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
              {usesMultiOutput && !loading && Boolean(consoleOutput.length) && (
                <div>
                  {(consoleOutput.log || []).map((log: IInstanceMultiOutputLog) => (
                    <ul className="tabs-basic console-output-tabs">
                      <li
                        className={`console-output-tab ${log.name === selectedLog.name ? 'selected' : ''}`}
                        onClick={() => setSelectedLog(log)}
                        ng-repeat="log in vm.consoleOutput"
                      >
                        {log.name}
                      </li>
                    </ul>
                  ))}
                  {selectedLog && <pre className="body-small">{selectedLog.output}</pre>}
                </div>
              )}
              {!usesMultiOutput && !loading && Boolean(consoleOutput.length) && (
                <div>
                  <pre className="body-small">{consoleOutput}</pre>
                </div>
              )}
              {exception && (
                <div>
                  <p>An error occurred trying to load console output. Please try again later.</p>
                  <p>Exception:</p>
                  <pre>{exception.message || exception}</pre>
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
              <button className="btn btn-link" onClick={fetchLogs}>
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
