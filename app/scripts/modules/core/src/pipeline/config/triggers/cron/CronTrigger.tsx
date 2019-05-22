import * as React from 'react';
import Select, { Option } from 'react-select';

import { CronMinutes } from './CronMinutes';
import { CronHourly } from './CronHourly';
import { CronDaily } from './CronDaily';
import { CronWeekly } from './CronWeekly';
import { CronMonthly } from './CronMonthly';
import { CronAdvance } from './CronAdvance';
import { ICronTriggerConfigProps } from './cronConfig';
import { BaseTrigger } from 'core/pipeline';

import './cronTrigger.less';

export interface ICronTriggerState {
  activeTab: string;
}

export class CronTrigger extends React.Component<ICronTriggerConfigProps, ICronTriggerState> {
  private tabOptions = [
    { label: 'minutes', value: 'minutes', regex: [/^(0 0\/\d+ \* 1\/1 \* \? \*)$/g] },
    { label: 'hourly', value: 'hourly', regex: [/^(0 \d+ 0\/\d+ 1\/1 \* \? \*)$/g] },
    { label: 'daily', value: 'daily', regex: [/^(0 \d+ \d+ 1\/\d+ \* \? \*)$/g, /^(0 \d+ \d+ \? \* MON\-FRI \*)$/g] },
    { label: 'weekly', value: 'weekly', regex: [/^(0 \d+ \d+ \? \*\s?([SUN,MON,TUE, WED, THU, FRI, SAT]*) \*)$/g] },
    {
      label: 'monthly',
      value: 'monthly',
      regex: [
        /^^(0 \d+ \d+ (\d+|1L|LW|L|W) 1\/\d+ \? \*)$/g,
        /^(0 \d+ \d+ \? 1\/\d+ (SUN|MON|TUE|WED|THU|FRI|SAT)(#[1-5]|L) \*)$/g,
      ],
    },
    { label: 'advanced', value: 'advanced', regex: [] },
  ];

  constructor(props: ICronTriggerConfigProps) {
    super(props);
    this.state = {
      activeTab: 'advanced',
    };
  }

  public componentDidMount() {
    const cronExpression = this.props.trigger.cronExpression || '0 0 0 1W 1/1 ? *';
    this.tabOptions.forEach(o => {
      o.regex.forEach(r => {
        if (r.exec(cronExpression)) {
          this.setState({ activeTab: o.value });
        }
      });
    });
    if (!this.props.trigger.cronExpression) {
      this.setState({ activeTab: 'minutes' });
      this.props.triggerUpdated({
        ...this.props.trigger,
        cronExpression: '0 0 0 1W 1/1 ? *',
      });
    }
  }

  private renderCronTypeContents = (activeTab: string) => {
    switch (activeTab) {
      case 'minutes':
        return <CronMinutes {...this.props} />;
      case 'hourly':
        return <CronHourly {...this.props} />;
      case 'daily':
        return <CronDaily {...this.props} />;
      case 'weekly':
        return <CronWeekly {...this.props} />;
      case 'monthly':
        return <CronMonthly {...this.props} />;
      case 'advanced':
        return <CronAdvance {...this.props} />;
      default:
        return <div>An error has occurred</div>;
    }
  };

  private CronTriggerContents = () => {
    const { activeTab } = this.state;
    return (
      <div className="form-group" style={{ marginTop: '10px' }}>
        <label className="col-md-3 sm-label-right">Frequency</label>
        <div className="col-md-9">
          <div className="cron-gen-main form-inline no-spel">
            <div className="row">
              <div className="col-md-6">
                <Select
                  className="form-control input-sm"
                  value={activeTab}
                  options={this.tabOptions}
                  onChange={(option: Option<string>) => {
                    this.setState({ activeTab: option.value });
                  }}
                  clearable={false}
                />
              </div>
            </div>
            {this.renderCronTypeContents(activeTab)}
          </div>
        </div>
      </div>
    );
  };

  public render() {
    const { CronTriggerContents } = this;
    return <BaseTrigger {...this.props} triggerContents={<CronTriggerContents />} />;
  }
}
