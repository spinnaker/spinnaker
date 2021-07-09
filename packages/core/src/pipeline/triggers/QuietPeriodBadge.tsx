import React from 'react';

import { Tooltip } from '../../presentation';

export interface IQuietPeriodBadgeProps {
  start: Date;
  end: Date;
}

const locale = 'en-US';
const dateOptions = {
  weekday: 'short',
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
};

export class QuietPeriodBadge extends React.Component<IQuietPeriodBadgeProps> {
  public render() {
    const { start, end } = this.props;

    if (!start || !end) {
      return null;
    }

    const now = new Date();
    const afterQuietPeriod = now > end;
    if (afterQuietPeriod) {
      return null;
    }

    const inQuietPeriod = start < now && !afterQuietPeriod;

    const quietPeriodRange = (
      <span>{`(${start.toLocaleString(locale, dateOptions)} - ${end.toLocaleString(locale, dateOptions)})`}</span>
    );
    const tooltipTemplate = inQuietPeriod ? (
      <span>
        This pipeline will not be automatically triggered until the end of the quiet period. {quietPeriodRange}
      </span>
    ) : (
      <span>This pipeline will not be automatically triggered during the quiet period. {quietPeriodRange}</span>
    );

    return (
      <Tooltip template={tooltipTemplate}>
        <i className="fa icon-calendar-warning" style={{ color: 'red' }} />
      </Tooltip>
    );
  }
}
