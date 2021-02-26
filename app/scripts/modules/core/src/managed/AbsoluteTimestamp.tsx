import { DateTime } from 'luxon';
import React, { memo } from 'react';

import { SETTINGS } from '../config';
import { CopyToClipboard } from '../utils';

export interface IAbsoluteTimestampProps {
  timestamp: DateTime;
  clickToCopy?: boolean;
}

const TIMEZONE = SETTINGS.feature.displayTimestampsInUserLocalTime ? undefined : SETTINGS.defaultTimeZone;

export const AbsoluteTimestamp = memo(
  ({ timestamp: timestampInOriginalZone, clickToCopy }: IAbsoluteTimestampProps) => {
    const timestamp = TIMEZONE ? timestampInOriginalZone.setZone(TIMEZONE) : timestampInOriginalZone;

    const fullTimestamp = timestamp.toFormat('yyyy-MM-dd HH:mm:ss ZZZZ');
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
