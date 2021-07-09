import React from 'react';
import Select, { Option } from 'react-select';

import { ICronTriggerConfigProps } from './cronConfig';
import { HOURS, MINUTES, WEEKDAYS } from './cronSelectOptions';
import { SystemTimezone } from '../../../../utils/SystemTimezone';

export interface ICronWeeklyState {
  weekly: string[];
  hour: string;
  minute: string;
}

export class CronWeekly extends React.Component<ICronTriggerConfigProps, ICronWeeklyState> {
  constructor(props: ICronTriggerConfigProps) {
    super(props);
    this.state = {
      weekly: [],
      hour: '0',
      minute: '0',
    };
  }

  public componentDidMount() {
    const regex = /^0 (\d+) (\d+) \? \* ((?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:,(?:MON|TUE|WED|THU|FRI|SAT|SUN))*) \*$/g;
    const matches = regex.exec(this.props.trigger.cronExpression);
    if (matches) {
      this.setState({
        minute: matches[1],
        hour: matches[2],
        weekly: matches[3].length > 0 ? matches[3].split(',') : [],
      });
    } else {
      const { minute, hour, weekly } = this.state;
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + minute + ' ' + hour + ' ? *' + (weekly.length > 0 ? ' ' + weekly.join() : '') + ' *',
      });
    }
  }

  private onUpdateWeeklyTrigger = (option: Option<string>): void => {
    const weekly = option.map((o: Option) => o.value);
    const { hour, minute } = this.state;
    this.setState({ weekly });
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression: '0 ' + minute + ' ' + hour + ' ? *' + (weekly.length > 0 ? ' ' + weekly.join() : '') + ' *',
    });
  };

  private onUpdateHourTrigger = (option: Option<string>) => {
    const hour = option.value;
    const { minute, weekly } = this.state;
    this.setState({ hour });
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression: '0 ' + minute + ' ' + hour + ' ? *' + (weekly.length > 0 ? ' ' + weekly.join() : '') + ' *',
    });
  };

  private onUpdateMinuteTrigger = (option: Option<string>) => {
    const minute = option.value;
    const { hour, weekly } = this.state;
    this.setState({ minute });
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression: '0 ' + minute + ' ' + hour + ' ? *' + (weekly.length > 0 ? ' ' + weekly.join() : '') + ' *',
    });
  };

  public render() {
    const { hour, minute, weekly } = this.state;
    return (
      <div>
        <div className="row">
          <div className="col-md-12">
            <Select
              options={WEEKDAYS}
              multi={true}
              clearable={false}
              value={weekly}
              onChange={this.onUpdateWeeklyTrigger}
            />
          </div>
        </div>
        <div className="row">
          <div className="col-md-12">
            <span>Start time </span>
            <Select
              className="visible-xs-inline-block visible-sm-inline-block visible-md-inline-block visible-lg-inline-block vertical-align-select"
              clearable={false}
              value={hour}
              options={HOURS}
              onChange={this.onUpdateHourTrigger}
            />{' '}
            <Select
              className="visible-xs-inline-block visible-sm-inline-block visible-md-inline-block visible-lg-inline-block vertical-align-select"
              clearable={false}
              value={minute}
              options={MINUTES}
              onChange={this.onUpdateMinuteTrigger}
            />{' '}
            <SystemTimezone />
          </div>
        </div>
      </div>
    );
  }
}
