import * as React from 'react';
import * as moment from 'moment';
import autoBindMethods from 'class-autobind-decorator';

import { IPipeline, ICronTrigger } from 'core/domain';
import { Popover } from 'core/presentation/Popover';
import { SETTINGS } from 'core/config/settings';
import { later } from 'core/utils/later/later';
import { timestamp } from 'core/utils/timeFormatters';

interface IProps {
  pipeline: IPipeline;
}

interface IState {
  nextScheduled: number;
  hasNextScheduled: boolean;
}

@autoBindMethods
export class NextRunTag extends React.Component<IProps, IState> {
  constructor(props: IProps) {
    super(props);

    this.state = this.updateSchedule();
  }

  private updateSchedule(): IState {
    if (this.props.pipeline) {
      const crons = (this.props.pipeline.triggers || []).filter(t => t.type === 'cron' && t.enabled) as ICronTrigger[];
      const nextTimes: number[] = [];
      crons.forEach(cron => {
        const parts = cron.cronExpression.split(' ');
        const hours = parts[2];
        if (!isNaN(parseInt(hours, 10))) {
          const allHours = hours.split('/');
          const tz = SETTINGS.defaultTimeZone;
          let offset = moment.tz.zone(tz).offset(Date.now());
          if (offset) {
            offset /= 60;
            const start = parseInt(allHours[0], 10);
            allHours[0] = ((start + offset) % 24).toString();
            parts[2] = allHours.join('/');
          }
        }
        const schedule = later.parse.cron(parts.join(' '), true);
        const nextRun = later.schedule(schedule).next(1);
        if (nextRun) {
          nextTimes.push(later.schedule(schedule).next(1).getTime());
        }
      });
      if (nextTimes.length) {
        return {
          hasNextScheduled: true,
          nextScheduled: Math.min(...nextTimes)
        };
      }
    }
    return {
      hasNextScheduled: false,
      nextScheduled: 0
    };
  }

  private handleMouseEnter() {
    this.setState(this.updateSchedule());
  }

  public render(): React.ReactElement<NextRunTag> {
    const nextDuration = moment(this.state.nextScheduled).fromNow();
    const visible = !this.props.pipeline.disabled && this.state.hasNextScheduled;
    return (
      <span style={{visibility: visible ? 'visible' : 'hidden'}} className="next-run-tag">
        <Popover value={`Next run: ${timestamp(this.state.nextScheduled)} (${nextDuration})`} placement="left">
          <span className="glyphicon glyphicon-time" onMouseEnter={this.handleMouseEnter}/>
        </Popover>
      </span>
    );
  }
}
