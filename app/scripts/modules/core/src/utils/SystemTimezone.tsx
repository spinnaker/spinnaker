import * as React from 'react';
import { DateTime } from 'luxon';

import { SETTINGS } from 'core/config/settings';

export const SystemTimezone = () => {
  const zone = SETTINGS.defaultTimeZone;
  const time = DateTime.local().setZone(zone);
  return <span>{time.offsetNameShort}</span>;
};
