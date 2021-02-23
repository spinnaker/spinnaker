import { DateTime } from 'luxon';
import React from 'react';

import { SETTINGS } from 'core/config/settings';

export const SystemTimezone = () => {
  const zone = SETTINGS.defaultTimeZone;
  const time = DateTime.local().setZone(zone);
  return <span>{time.offsetNameShort}</span>;
};
