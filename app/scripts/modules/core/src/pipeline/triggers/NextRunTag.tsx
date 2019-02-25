import * as React from 'react';
import { DateTime } from 'luxon';

import { IPipeline, ICronTrigger } from 'core/domain';
import { Popover } from 'core/presentation/Popover';
import { SETTINGS } from 'core/config/settings';
import { later } from 'core/utils/later/later';
import { timestamp, relativeTime } from 'core/utils/timeFormatters';

export interface INextRunTagProps {
  pipeline: IPipeline;
}

export interface INextRunTagState {
  nextScheduled: number;
  hasNextScheduled: boolean;
}

export class NextRunTag extends React.Component<INextRunTagProps, INextRunTagState> {
  constructor(props: INextRunTagProps) {
    super(props);

    this.state = this.updateSchedule();
  }

  private updateSchedule(): INextRunTagState {
    if (this.props.pipeline) {
      const crons = (this.props.pipeline.triggers || []).filter(
        (t: ICronTrigger) => t.type === 'cron' && t.enabled && t.cronExpression,
      ) as ICronTrigger[];
      const nextTimes: number[] = [];
      crons.forEach(cron => {
        const timezoneOffsetInMs = DateTime.local().setZone(SETTINGS.defaultTimeZone).offset * 60 * 1000;
        const nextRun = later
          .schedule(later.parse.cron(cron.cronExpression, true))
          .next(1, new Date(Date.now() + timezoneOffsetInMs));

        if (nextRun) {
          nextTimes.push(nextRun.getTime() - timezoneOffsetInMs);
        }
      });
      if (nextTimes.length) {
        return {
          hasNextScheduled: true,
          nextScheduled: Math.min(...nextTimes),
        };
      }
    }
    return {
      hasNextScheduled: false,
      nextScheduled: 0,
    };
  }

  private handleMouseEnter = () => {
    this.setState(this.updateSchedule());
  };

  public render(): React.ReactElement<NextRunTag> {
    const nextDuration = relativeTime(this.state.nextScheduled);
    const visible = !this.props.pipeline.disabled && this.state.hasNextScheduled;
    return (
      <span style={{ visibility: visible ? 'visible' : 'hidden' }} className="next-run-tag">
        <Popover value={`Next run: ${timestamp(this.state.nextScheduled)} (${nextDuration})`} placement="left">
          <span className="glyphicon glyphicon-time" onMouseEnter={this.handleMouseEnter} />
        </Popover>
      </span>
    );
  }
}
