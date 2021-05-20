import { DateTime, Duration } from 'luxon';
import React, { memo, useEffect, useState } from 'react';

import { SETTINGS } from '../config';
import { Tooltip, useInterval } from '../presentation';
import { CopyToClipboard, timeDiffToString } from '../utils';

export interface IRelativeTimestampProps {
  timestamp: DateTime | string;
  clickToCopy?: boolean;
  removeStyles?: boolean;
  delayShow?: number;
  withSuffix?: boolean;
}

const TIMEZONE = SETTINGS.feature.displayTimestampsInUserLocalTime ? undefined : SETTINGS.defaultTimeZone;

export const DurationRender: React.FC<{ startedAt: string; completedAt?: string }> = ({ startedAt, completedAt }) => {
  const [, setRefresh] = React.useState<boolean>(false);
  useInterval(
    () => {
      if (!completedAt) {
        setRefresh((state) => !state);
      }
    },
    !completedAt ? 1000 : 0,
  );
  const startAtDateTime = DateTime.fromISO(startedAt);
  const endTime = !completedAt ? DateTime.utc() : DateTime.fromISO(completedAt);
  return <>{timeDiffToString(startAtDateTime, endTime)}</>;
};

const formatTimestamp = (timestamp: DateTime, distance: Duration, withSuffix: boolean) => {
  const suffix = withSuffix ? ' ago' : '';
  if (distance.years || distance.months) {
    let currentTime = DateTime.local();
    if (TIMEZONE) {
      currentTime = currentTime.setZone(TIMEZONE);
    }
    if (timestamp.year === currentTime.year) {
      return timestamp.toFormat('MMM d');
    } else {
      return timestamp.toFormat('MMM d, y');
    }
  } else if (distance.days) {
    return distance.toFormat('d') + 'd' + suffix;
  } else if (distance.hours) {
    return distance.toFormat('h') + 'h' + suffix;
  } else if (distance.minutes) {
    return distance.toFormat('m') + 'm' + suffix;
  } else if (distance.seconds) {
    return distance.toFormat('s') + 's' + suffix;
  } else {
    return null;
  }
};

const getDistanceFromNow = (timestamp: DateTime) =>
  timestamp.diffNow().negate().shiftTo('years', 'months', 'days', 'hours', 'minutes', 'seconds');

export const RelativeTimestamp = memo(
  ({
    timestamp: originalTimestamp,
    clickToCopy,
    delayShow,
    removeStyles,
    withSuffix = false,
  }: IRelativeTimestampProps) => {
    const dateTimeTimestamp =
      typeof originalTimestamp === 'string' ? DateTime.fromISO(originalTimestamp) : originalTimestamp;
    const timestamp = TIMEZONE ? dateTimeTimestamp.setZone(TIMEZONE) : dateTimeTimestamp;
    const [formattedTimestamp, setFormattedTimestamp] = useState(
      formatTimestamp(timestamp, getDistanceFromNow(timestamp), withSuffix),
    );

    const updateTimestamp = () => {
      setFormattedTimestamp(formatTimestamp(timestamp, getDistanceFromNow(timestamp), withSuffix));
    };

    useInterval(updateTimestamp, 1000);
    useEffect(updateTimestamp, [timestamp]);
    if (!formattedTimestamp) {
      return null;
    }

    const absoluteTimestamp = timestamp.toFormat('yyyy-MM-dd HH:mm:ss ZZZZ');
    const relativeTimestamp = (
      <span
        className={removeStyles ? undefined : 'text-regular text-italic'}
        style={removeStyles ? undefined : { fontSize: 13, lineHeight: 1 }}
      >
        {formattedTimestamp}
      </span>
    );

    if (clickToCopy) {
      return (
        <CopyToClipboard
          buttonInnerNode={relativeTimestamp}
          text={absoluteTimestamp}
          toolTip={`${absoluteTimestamp} (click to copy)`}
        />
      );
    } else {
      return (
        <Tooltip value={absoluteTimestamp} delayShow={delayShow}>
          {relativeTimestamp}
        </Tooltip>
      );
    }
  },
);
