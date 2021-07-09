import React from 'react';
import Select, { Option } from 'react-select';

import { ICronTriggerConfigProps } from './cronConfig';
import { HOURS, MINUTES, MONTH_WEEKS, MONTHS_DAYS_WITH_LASTS, WEEKDAYS } from './cronSelectOptions';
import { SystemTimezone } from '../../../../utils/SystemTimezone';

export interface ICronMonthlyState {
  subTab: string;
  hour: string;
  minute: string;
  specificDay?: ISpecificDay;
  specificWeekDay?: ISpecificWeekDay;
}

interface ISpecificDay {
  day: string;
  month: string;
}

interface ISpecificWeekDay {
  day: string;
  month: string;
  monthWeek: string;
}

export class CronMonthly extends React.Component<ICronTriggerConfigProps, ICronMonthlyState> {
  constructor(props: ICronTriggerConfigProps) {
    super(props);
    this.state = {
      hour: '0',
      minute: '0',
      subTab: 'specificDay',
      specificDay: {
        day: '1W',
        month: '1',
      },
      specificWeekDay: {
        day: 'SUN',
        month: '1',
        monthWeek: '#1',
      },
    };
  }

  public componentDidMount() {
    const specificDayRegex = /0 (\d+) (\d+) (\d+|1L|LW|L|W) 1\/(\d+) \? \*/g;
    const specificWeekDayRegex = /0 (\d+) (\d+) \? 1\/(\d+) (SUN|MON|TUE|WED|THU|FRI|SAT)(#[1-5]|L) \*/g;
    const specificDayMatches = specificDayRegex.exec(this.props.trigger.cronExpression);
    const specificWeekDayMatches = specificWeekDayRegex.exec(this.props.trigger.cronExpression);
    if (specificDayMatches) {
      this.setState({
        subTab: 'specificDay',
        minute: specificDayMatches[1],
        hour: specificDayMatches[2],
        specificDay: {
          day: specificDayMatches[3],
          month: specificDayMatches[4],
        },
      });
    } else if (specificWeekDayMatches) {
      this.setState({
        subTab: 'specificWeekDay',
        minute: specificWeekDayMatches[1],
        hour: specificWeekDayMatches[2],
        specificWeekDay: {
          month: specificWeekDayMatches[3],
          day: specificWeekDayMatches[4],
          monthWeek: specificWeekDayMatches[5],
        },
      });
    } else {
      const { minute, hour, specificDay } = this.state;
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + minute + ' ' + hour + ' ' + specificDay.day + ' 1/' + specificDay.month + ' ? *',
      });
    }
  }

  private onUpdateSubTabTrigger = (event: React.ChangeEvent<HTMLInputElement>) => {
    const subTab = event.target.value;
    this.setState({ subTab });
    const { hour, minute } = this.state;
    if (subTab === 'specificDay') {
      const { specificDay } = this.state;
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + minute + ' ' + hour + ' ' + specificDay.day + ' 1/' + specificDay.month + ' ? *',
      });
    } else if (subTab === 'specificWeekDay') {
      const { specificWeekDay } = this.state;
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression:
          '0 ' +
          minute +
          ' ' +
          hour +
          ' ? 1/' +
          specificWeekDay.month +
          ' ' +
          specificWeekDay.day +
          specificWeekDay.monthWeek +
          ' *',
      });
    }
  };

  private onUpdateSpecificDayDayTrigger = (option: Option<string>) => {
    const day = option.value;
    this.setState({
      specificDay: {
        ...this.state.specificDay,
        day,
      },
    });
    const { hour, minute, specificDay } = this.state;
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression: '0 ' + minute + ' ' + hour + ' ' + day + ' 1/' + specificDay.month + ' ? *',
    });
  };

  private onUpdateSpecificDayMonthTrigger = (event: React.ChangeEvent<HTMLInputElement>) => {
    const month = event.target.value;
    this.setState({
      specificDay: {
        ...this.state.specificDay,
        month,
      },
    });
    const { hour, minute, specificDay } = this.state;
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression: '0 ' + minute + ' ' + hour + ' ' + specificDay.day + ' 1/' + month + ' ? *',
    });
  };

  private onUpdateSpecificWeekDayMonthWeekTrigger = (option: Option<string>) => {
    const monthWeek = option.value;
    this.setState({
      specificWeekDay: {
        ...this.state.specificWeekDay,
        monthWeek,
      },
    });
    const { hour, minute, specificWeekDay } = this.state;
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression:
        '0 ' + minute + ' ' + hour + ' ? 1/' + specificWeekDay.month + ' ' + specificWeekDay.day + monthWeek + ' *',
    });
  };

  private onUpdateSpecificWeekDayDayTrigger = (option: Option<string>) => {
    const day = option.value;
    this.setState({
      specificWeekDay: {
        ...this.state.specificWeekDay,
        day,
      },
    });
    const { hour, minute, specificWeekDay } = this.state;
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression:
        '0 ' + minute + ' ' + hour + ' ? 1/' + specificWeekDay.month + ' ' + day + specificWeekDay.monthWeek + ' *',
    });
  };

  private onUpdateSpecificWeekDayMonthTrigger = (event: React.ChangeEvent<HTMLInputElement>) => {
    const month = event.target.value;
    this.setState({
      specificWeekDay: {
        ...this.state.specificWeekDay,
        month,
      },
    });
    const { hour, minute, specificWeekDay } = this.state;
    this.props.triggerUpdated({
      ...this.props.trigger,
      cronExpression:
        '0 ' + minute + ' ' + hour + ' ? 1/' + month + ' ' + specificWeekDay.day + specificWeekDay.monthWeek + ' *',
    });
  };

  private onUpdateHourTrigger = (option: Option<string>) => {
    const hour = option.value;
    this.setState({
      hour,
    });
    const { minute, subTab } = this.state;
    if (subTab === 'specificDay') {
      const { specificDay } = this.state;
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + minute + ' ' + hour + ' ' + specificDay.day + ' 1/' + specificDay.month + ' ? *',
      });
    } else if (subTab === 'specificWeekDay') {
      const { specificWeekDay } = this.state;
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression:
          '0 ' +
          minute +
          ' ' +
          hour +
          ' ? 1/' +
          specificWeekDay.month +
          ' ' +
          specificWeekDay.day +
          specificWeekDay.monthWeek +
          ' *',
      });
    }
  };

  private onUpdateMinuteTrigger = (option: Option<string>) => {
    const minute = option.value;
    this.setState({
      minute,
    });
    const { hour, subTab } = this.state;
    if (subTab === 'specificDay') {
      const { specificDay } = this.state;
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 ' + minute + ' ' + hour + ' ' + specificDay.day + ' 1/' + specificDay.month + ' ? *',
      });
    } else if (subTab === 'specificWeekDay') {
      const { specificWeekDay } = this.state;
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression:
          '0 ' +
          minute +
          ' ' +
          hour +
          ' ? 1/' +
          specificWeekDay.month +
          ' ' +
          specificWeekDay.day +
          specificWeekDay.monthWeek +
          ' *',
      });
    }
  };

  public render() {
    const { hour, minute, specificDay, specificWeekDay, subTab } = this.state;
    return (
      <div>
        <div className="row">
          <div className="col-md-12">
            <input
              type="radio"
              value="specificDay"
              onChange={this.onUpdateSubTabTrigger}
              checked={subTab === 'specificDay'}
            />
            <span>On the </span>
            <Select
              className="visible-xs-inline-block visible-sm-inline-block visible-md-inline-block visible-lg-inline-block vertical-align-select select-month-days"
              clearable={false}
              disabled={subTab !== 'specificDay'}
              onChange={this.onUpdateSpecificDayDayTrigger}
              value={specificDay.day}
              options={MONTHS_DAYS_WITH_LASTS}
            />
            <span> of every </span>
            <input
              className="form-control input-sm"
              disabled={subTab !== 'specificDay'}
              type="number"
              min="1"
              max="11"
              onChange={this.onUpdateSpecificDayMonthTrigger}
              required={subTab === 'specificDay'}
              value={specificDay.month}
            />
            <span> month(s)</span>
          </div>
        </div>
        <div className="row">
          <div className="col-md-12">
            <input
              type="radio"
              value="specificWeekDay"
              onChange={this.onUpdateSubTabTrigger}
              checked={subTab === 'specificWeekDay'}
            />
            <Select
              className="visible-xs-inline-block visible-sm-inline-block visible-md-inline-block visible-lg-inline-block vertical-align-select select-month-days"
              clearable={false}
              disabled={subTab !== 'specificWeekDay'}
              onChange={this.onUpdateSpecificWeekDayMonthWeekTrigger}
              value={specificWeekDay.monthWeek}
              options={MONTH_WEEKS}
            />{' '}
            <Select
              className="visible-xs-inline-block visible-sm-inline-block visible-md-inline-block visible-lg-inline-block vertical-align-select select-weekdays"
              clearable={false}
              disabled={subTab !== 'specificWeekDay'}
              onChange={this.onUpdateSpecificWeekDayDayTrigger}
              value={specificWeekDay.day}
              options={WEEKDAYS}
            />
            <span> of every </span>
            <input
              className="form-control input-sm"
              disabled={subTab !== 'specificWeekDay'}
              type="number"
              min="1"
              max="11"
              onChange={this.onUpdateSpecificWeekDayMonthTrigger}
              required={subTab === 'specificWeekDay'}
              value={specificWeekDay.month}
            />
            <span> month(s)</span>
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
