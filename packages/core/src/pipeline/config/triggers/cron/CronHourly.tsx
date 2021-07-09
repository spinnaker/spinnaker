import React from 'react';

import { ICronTriggerConfigProps } from './cronConfig';

export interface ICronHourlyState {
  hours?: string;
  minutes?: string;
}

export class CronHourly extends React.Component<ICronTriggerConfigProps, ICronHourlyState> {
  constructor(props: ICronTriggerConfigProps) {
    super(props);
    this.state = {
      hours: '1',
      minutes: '1',
    };
  }

  public componentDidMount() {
    const regex = /0 (\d+) 0\/(\d+) 1\/1 \* \? \*/g;
    const matches = regex.exec(this.props.trigger.cronExpression);
    if (matches) {
      this.setState({
        minutes: matches[1],
        hours: matches[2],
      });
    } else {
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + this.state.minutes + ' 0/' + this.state.hours + ' 1/1 * ? *',
      });
    }
  }

  private onUpdateHourlyTrigger = (hours: string) => {
    this.setState({ hours });
    const { minutes } = this.state;
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression: '0 ' + minutes + ' 0/' + hours + ' 1/1 * ? *',
    });
  };

  private onUpdateMinuteTrigger = (minutes: string) => {
    this.setState({ minutes });
    const { hours } = this.state;
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression: '0 ' + minutes + ' 0/' + hours + ' 1/1 * ? *',
    });
  };

  public render() {
    const { hours, minutes } = this.state;
    return (
      <div>
        <div className="row">
          <div className="col-md-12">
            <span>Every </span>
            <input
              className="form-control input-sm"
              type="number"
              min="1"
              max="23"
              onChange={(event: React.ChangeEvent<HTMLInputElement>) => this.onUpdateHourlyTrigger(event.target.value)}
              required={true}
              value={hours}
            />
            <span> hour(s) on minute </span>
            <input
              className="form-control input-sm"
              type="number"
              min="0"
              max="59"
              required={true}
              onChange={(event: React.ChangeEvent<HTMLInputElement>) => this.onUpdateMinuteTrigger(event.target.value)}
              value={minutes}
            />
          </div>
        </div>
      </div>
    );
  }
}
