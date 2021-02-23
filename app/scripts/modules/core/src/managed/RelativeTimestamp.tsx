import { DateTime, Duration } from 'luxon';
import React, { memo, useEffect, useState } from 'react';

import { SETTINGS } from '../config';
import { Tooltip, useInterval } from '../presentation';
import { CopyToClipboard, timeDiffToString } from '../utils';

export interface IRelativeTimestampProps {
  timestamp: DateTime;
  clickToCopy?: boolean;
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
    !completedAt ? 1000 : undefined,
  );
  const startAtDateTime = DateTime.fromISO(startedAt);
  const endTime = !completedAt ? DateTime.utc() : DateTime.fromISO(completedAt);
  return <>{timeDiffToString(startAtDateTime, endTime)}</>;
};

const formatTimestamp = (timestamp: DateTime, distance: Duration) => {
  if (distance.years || distance.months) {
    if (timestamp.year === DateTime.local().setZone(TIMEZONE).year) {
      return timestamp.toFormat('MMM d');
    } else {
      return timestamp.toFormat('MMM d, y');
    }
  } else if (distance.days) {
    return distance.toFormat('d') + 'd';
  } else if (distance.hours) {
    return distance.toFormat('h') + 'h';
  } else if (distance.minutes) {
    return distance.toFormat('m') + 'm';
  } else if (distance.seconds) {
    return distance.toFormat('s') + 's';
  } else {
    return null;
  }
};

const getDistanceFromNow = (timestamp: DateTime) =>
  timestamp.diffNow().negate().shiftTo('years', 'months', 'days', 'hours', 'minutes', 'seconds');

export const RelativeTimestamp = memo(
  ({ timestamp: timestampInOriginalZone, clickToCopy }: IRelativeTimestampProps) => {
    const timestamp = timestampInOriginalZone.setZone(TIMEZONE);
    const [formattedTimestamp, setFormattedTimestamp] = useState(
      formatTimestamp(timestamp, getDistanceFromNow(timestamp)),
    );

    const updateTimestamp = () => {
      setFormattedTimestamp(formatTimestamp(timestamp, getDistanceFromNow(timestamp)));
    };

    useInterval(updateTimestamp, 1000);
    useEffect(updateTimestamp, [timestamp]);

    if (!formattedTimestamp) {
      return null;
    }

    const absoluteTimestamp = timestamp.toFormat('yyyy-MM-dd HH:mm:ss ZZZZ');
    const relativeTimestamp = (
      <span className="text-regular text-italic" style={{ fontSize: 13, lineHeight: 1 }}>
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
      return <Tooltip value={absoluteTimestamp}>{relativeTimestamp}</Tooltip>;
    }
  },
);
