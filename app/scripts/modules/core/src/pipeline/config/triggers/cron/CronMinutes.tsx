import React from 'react';
import Select, { Option } from 'react-select';

import { ICronTriggerConfigProps } from './cronConfig';

export interface ICronMinutesState {
  minutes?: string;
}

export class CronMinutes extends React.Component<ICronTriggerConfigProps, ICronMinutesState> {
  private minuteOptions: string[] = ['1', '2', '3', '4', '5', '6', '10', '12', '15', '20', '30'];

  constructor(props: ICronTriggerConfigProps) {
    super(props);
    this.state = { minutes: '1' };
  }

  public componentDidMount() {
    const regex = /0 0\/(\d+) \* 1\/1 \* \? \*/g;
    const matches = regex.exec(this.props.trigger.cronExpression);
    if (matches) {
      this.setState({ minutes: matches[1] });
    } else {
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 0/' + this.state.minutes + ' * 1/1 * ? *',
      });
    }
  }

  private onUpdateTrigger = (option: Option<string>) => {
    const minutes = option.value;
    this.setState({ minutes });
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression: '0 0/' + minutes + ' * 1/1 * ? *',
    });
  };

  public render() {
    const { minutes } = this.state;
    return (
      <div>
        <div className="row">
          <div className="col-md-12">
            <span>Every </span>
            <Select
              className="visible-xs-inline-block visible-sm-inline-block visible-md-inline-block visible-lg-inline-block vertical-align-select"
              value={minutes}
              options={this.minuteOptions.map((j) => ({ label: j, value: j }))}
              onChange={this.onUpdateTrigger}
              clearable={false}
            />
            <span> minute(s)</span>
          </div>
        </div>
      </div>
    );
  }
}
