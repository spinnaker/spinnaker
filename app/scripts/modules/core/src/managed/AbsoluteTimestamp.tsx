import { DateTime } from 'luxon';
import React, { memo } from 'react';

import { SETTINGS } from '../config';
import { CopyToClipboard } from '../utils';
import { ABSOLUTE_TIME_FORMAT } from './utils/defaults';

export interface IAbsoluteTimestampProps {
  timestamp: DateTime;
  clickToCopy?: boolean;
}

const TIMEZONE = SETTINGS.feature.displayTimestampsInUserLocalTime ? undefined : SETTINGS.defaultTimeZone;

export const AbsoluteTimestamp = memo(
  ({ timestamp: timestampInOriginalZone, clickToCopy }: IAbsoluteTimestampProps) => {
    const timestamp = TIMEZONE ? timestampInOriginalZone.setZone(TIMEZONE) : timestampInOriginalZone;

    const fullTimestamp = timestamp.toFormat(ABSOLUTE_TIME_FORMAT);
    const formattedTimestamp = timestamp.toFormat('MMM d, y HH:mm');
    const timestampElement = <span>{formattedTimestamp}</span>;

    if (clickToCopy) {
      return (
        <span>
          {timestampElement}
          <CopyToClipboard text={fullTimestamp} toolTip={`${fullTimestamp} (click to copy)`} />
        </span>
      );
    } else {
      return timestampElement;
    }
  },
);
