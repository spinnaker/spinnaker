import * as React from 'react';

import { IModalComponentProps } from '@spinnaker/core';

export interface IRunJobLogsModalProps extends IModalComponentProps {
  logs: string;
}

export class RunJobLogsModal extends React.Component<IRunJobLogsModalProps> {
  constructor(props: IRunJobLogsModalProps) {
    super(props);
  }

  public render() {
    const { dismissModal } = this.props;
    return (
      <div className="flex-fill">
        <div className="modal-header">
          <h3>Execution Logs</h3>
        </div>
        <div className="modal-body flex-fill">
          <pre className="body-small flex-fill">{this.props.logs || ''}</pre>
        </div>
        <div className="modal-footer">
          <button className="btn btn-default" onClick={dismissModal}>
            Close
          </button>
        </div>
      </div>
    );
  }
}
