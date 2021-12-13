import type { DateTimeFormatOptions } from 'luxon';
import React from 'react';

import { Tooltip } from '../../presentation';
import { useQuietPeriod } from './useQuietPeriod.hook';

const locale = 'en-US';
const dateOptions: DateTimeFormatOptions = {
  weekday: 'short',
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
};

export function QuietPeriodBadge() {
  const quietPeriod = useQuietPeriod();
  if (quietPeriod.currentStatus === 'NO_QUIET_PERIOD' || quietPeriod.currentStatus === 'UNKNOWN') {
    return null;
  }

  const start = new Date(quietPeriod.startTime);
  const end = new Date(quietPeriod.endTime);

  const message =
    quietPeriod.currentStatus === 'DURING_QUIET_PERIOD'
      ? 'This pipeline will not be automatically triggered until the end of the quiet period. '
      : 'This pipeline will not be automatically triggered during the quiet period. ';

  const template = (
    <span>
      {message}
      <span>{`(${start.toLocaleString(locale, dateOptions)} - ${end.toLocaleString(locale, dateOptions)})`}</span>
    </span>
  );

  return (
    <Tooltip template={template}>
      <i className="fa icon-calendar-warning" style={{ color: 'red' }} />
    </Tooltip>
  );
}
