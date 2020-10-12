import React from 'react';

export interface ITaskReasonProps {
  onChange: (reason: string) => void;
  reason: string;
}

export class TaskReason extends React.Component<ITaskReasonProps> {
  public render() {
    return (
      <div className="row" style={{ marginTop: '10px', marginBottom: '10px' }}>
        <div className="col-md-3 sm-label-right">Reason</div>
        <div className="col-md-7">
          <textarea
            className="form-control"
            value={this.props.reason}
            onChange={(event) => this.props.onChange(event.target.value)}
            ng-model="vm.command.reason"
            rows={3}
            placeholder="(Optional) anything that might be helpful to explain the reason for this change; HTML is okay"
          />
        </div>
      </div>
    );
  }
}
