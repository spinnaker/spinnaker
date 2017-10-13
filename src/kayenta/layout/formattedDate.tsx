import * as React from 'react';
import * as moment from 'moment-timezone';

import { SETTINGS } from '@spinnaker/core';

export interface IDateLabelProps {
  dateIso: string;
  format?: string;
}

const DEFAULT_FORMAT = 'MM/DD/YY @ HH:mm';

export default function FormattedDate({ dateIso, format }: IDateLabelProps) {
  if (dateIso) {
    return <span>{moment.tz(dateIso, SETTINGS.defaultTimeZone).format(format || DEFAULT_FORMAT)}</span>;
  } else {
    return <span>N/A</span>;
  }
}

