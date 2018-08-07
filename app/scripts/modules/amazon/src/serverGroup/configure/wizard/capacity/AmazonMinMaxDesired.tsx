import * as React from 'react';
import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

export interface IMinMaxDesiredProps {
  command: IAmazonServerGroupCommand;
  fieldChanged: (fieldName: string, value: string) => void;
}

export interface IAmazonMinMaxDesiredState {}

export class AmazonMinMaxDesired extends React.Component<IMinMaxDesiredProps, IAmazonMinMaxDesiredState> {
  public render() {
    const { command, fieldChanged } = this.props;
    return (
      <div>
        <div className="form-group">
          <div className="col-md-2">Min</div>
          <div className="col-md-2">Max</div>
          <div className="col-md-2">Desired</div>
        </div>
        <div className="form-group">
          <div className="col-md-2">
            <input
              type="number"
              className="form-control input-sm"
              value={command.capacity.min}
              min="0"
              max={command.capacity.max}
              onChange={e => fieldChanged('min', e.target.value)}
              required={true}
            />
          </div>
          <div className="col-md-2">
            <input
              type="number"
              className="form-control input-sm"
              value={command.capacity.max}
              min={command.capacity.min}
              onChange={e => fieldChanged('max', e.target.value)}
              required={true}
            />
          </div>
          <div className="col-md-2">
            <input
              type="number"
              className="form-control input-sm"
              value={command.capacity.desired}
              min={command.capacity.min}
              max={command.capacity.max}
              onChange={e => fieldChanged('desired', e.target.value)}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  }
}
