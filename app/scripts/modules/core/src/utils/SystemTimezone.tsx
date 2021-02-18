import { SETTINGS } from 'core/config/settings';
import { DateTime } from 'luxon';
import React from 'react';

export const SystemTimezone = () => {
  const zone = SETTINGS.defaultTimeZone;
  const time = DateTime.local().setZone(zone);
  return <span>{time.offsetNameShort}</span>;
};
