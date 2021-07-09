import React from 'react';
import Select, { Option } from 'react-select';

import { ICronTriggerConfigProps } from './cronConfig';
import { HOURS, MINUTES } from './cronSelectOptions';
import { SystemTimezone } from '../../../../utils/SystemTimezone';

export interface ICronDailyState {
  subTab: string;
  everyDaysDays: string;
  hour: string;
  minute: string;
}

export class CronDaily extends React.Component<ICronTriggerConfigProps, ICronDailyState> {
  constructor(props: ICronTriggerConfigProps) {
    super(props);
    this.state = {
      subTab: 'everyDays',
      everyDaysDays: '1',
      hour: '0',
      minute: '0',
    };
  }

  public componentDidMount() {
    const everyDaysRegex = /0 (\d+) (\d+) 1\/(\d+) \* \? \*/g;
    const everyWeekDayRegex = /0 (\d+) (\d+) \? \* MON-FRI \*/g;
    const everyDaysMatches = everyDaysRegex.exec(this.props.trigger.cronExpression);
    const everyWeekDayMatches = everyWeekDayRegex.exec(this.props.trigger.cronExpression);
    if (everyDaysMatches) {
      this.setState({
        subTab: 'everyDays',
        minute: everyDaysMatches[1],
        hour: everyDaysMatches[2],
        everyDaysDays: everyDaysMatches[3],
      });
    } else if (everyWeekDayMatches) {
      this.setState({
        subTab: 'everyWeekDay',
        minute: everyWeekDayMatches[1],
        hour: everyWeekDayMatches[2],
      });
    } else {
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 0 0 1/' + this.state.everyDaysDays + ' * ? *',
      });
    }
  }

  private onUpdateSubTabTrigger = (event: React.ChangeEvent<HTMLInputElement>) => {
    const subTab = event.target.value;
    this.setState({ subTab });
    if (subTab === 'everyDays') {
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 0 0 1/' + this.state.everyDaysDays + ' * ? *',
      });
    } else if (subTab === 'everyWeekDay') {
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + this.state.minute + ' ' + this.state.hour + ' ? * MON-FRI *',
      });
    }
  };

  private onUpdateEveryDaysDaysTrigger = (event: React.ChangeEvent<HTMLInputElement>) => {
    const everyDaysDays = event.target.value;
    this.setState({ everyDaysDays });
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression: '0 0 0 1/' + everyDaysDays + ' * ? *',
    });
  };

  private onUpdateHourTrigger = (option: Option<string>) => {
    const hour = option.value;
    const { everyDaysDays, minute, subTab } = this.state;
    this.setState({ hour });
    if (subTab === 'everyDays') {
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + minute + ' ' + hour + ' 1/' + everyDaysDays + ' * ? *',
      });
    } else if (subTab === 'everyWeekDay') {
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + minute + ' ' + hour + ' ? * MON-FRI *',
      });
    }
  };

  private onUpdateMinuteTrigger = (option: Option<string>) => {
    const minute = option.value;
    const { everyDaysDays, hour, subTab } = this.state;
    this.setState({ minute });
    if (subTab === 'everyDays') {
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + minute + ' ' + hour + ' 1/' + everyDaysDays + ' * ? *',
      });
    } else if (subTab === 'everyWeekDay') {
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + minute + ' ' + hour + ' ? * MON-FRI *',
      });
    }
  };

  public render() {
    const { everyDaysDays, hour, minute, subTab } = this.state;
    return (
      <div>
        <div className="row">
          <div className="col-md-12">
            <input
              type="radio"
              value="everyDays"
              onChange={this.onUpdateSubTabTrigger}
              checked={subTab === 'everyDays'}
            />
            <span>Every </span>
            <input
              className="form-control input-sm"
              type="number"
              min="1"
              max="31"
              disabled={subTab !== 'everyDays'}
              onChange={this.onUpdateEveryDaysDaysTrigger}
              required={subTab === 'everyDays'}
              value={everyDaysDays}
            />
            <span> day(s)</span>
          </div>
        </div>
        <div className="row">
          <div className="col-md-12">
            <input
              type="radio"
              value="everyWeekDay"
              onChange={this.onUpdateSubTabTrigger}
              checked={subTab === 'everyWeekDay'}
            />
            Every week day
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
