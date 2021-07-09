import { module } from 'angular';
import { formatDistanceToNow } from 'date-fns';
import { DateTime, Duration } from 'luxon';
import { react2angular } from 'react2angular';

import { SystemTimezone } from './SystemTimezone';
import { SETTINGS } from '../config/settings';

// Luxon supports up to 100 million days after epoch start
const MAX_VALID_INPUT = 8640000000000000;
const isInputValid = (input: any) => !(!input || isNaN(input) || input < 0 || input > MAX_VALID_INPUT);

export function duration(input: number) {
  if (!isInputValid(input)) {
    return '-';
  }
  // formatting does not support optionally omitting fields so we have to get
  // a little weird with the format strings and the durations we send into them
  const baseDuration = Duration.fromMillis(input);
  const days = Math.floor(baseDuration.as('days'));
  // remove any days - we will add them manually if needed
  const thisDuration = baseDuration.minus({ days: Math.floor(baseDuration.as('days')) });
  const format = thisDuration.days || Math.floor(thisDuration.as('hours')) ? 'hh:mm:ss' : 'mm:ss';
  let dayLabel = '';
  if (thisDuration.isValid) {
    if (days > 0) {
      dayLabel = days + 'd';
    }
  }
  return thisDuration.isValid ? dayLabel + thisDuration.toFormat(format) : '-';
}

export function timeDiffToString(startTime: DateTime, endTime: DateTime) {
  const duration = endTime.diff(startTime).shiftTo('days', 'hours', 'minutes', 'seconds');

  const formatStrings = [];
  let forcePush = false;
  const addUnits = (unit: 'days' | 'hours' | 'minutes', unitFormat: string) => {
    if (duration[unit] || forcePush) {
      formatStrings.push(unitFormat);
      forcePush = true;
    }
  };
  addUnits('days', `d'd'`);
  addUnits('hours', `h'h'`);
  addUnits('minutes', `m'm'`);
  formatStrings.push(`s's'`);
  return duration.toFormat(formatStrings.join(' '));
}

export function timestamp(input: any) {
  if (!isInputValid(input)) {
    return '-';
  }
  const tz = SETTINGS.feature.displayTimestampsInUserLocalTime ? undefined : SETTINGS.defaultTimeZone;
  const thisMoment = DateTime.fromMillis(parseInt(input, 10), { zone: tz });
  return thisMoment.isValid ? thisMoment.toFormat('yyyy-MM-dd HH:mm:ss ZZZZ') : '-';
}

export function relativeTime(input?: number) {
  if (!isInputValid(input)) {
    return '-';
  }
  const now = Date.now();
  const inFuture = input > now;
  const thisMoment = DateTime.fromMillis(input);
  const baseText = formatDistanceToNow(thisMoment.toJSDate(), { includeSeconds: true });
  return thisMoment.isValid ? `${inFuture ? 'in ' : ''}${baseText}${inFuture ? '' : ' ago'}` : '-';
}

export function timePickerTime(input: any) {
  if (input && !isNaN(input.hours) && !isNaN(input.minutes)) {
    const hours = parseInt(input.hours, 10);
    const minutes = parseInt(input.minutes, 10);

    let result = '';
    if (hours < 10) {
      result += '0';
    }
    result += hours + ':';
    if (minutes < 10) {
      result += '0';
    }
    result += minutes;
    return result;
  }
  return '-';
}

export const TIME_FORMATTERS = 'spinnaker.core.utils.timeFormatters';
module(TIME_FORMATTERS, [])
  .filter('timestamp', () => timestamp)
  .filter('relativeTime', () => relativeTime)
  .filter('duration', () => duration)
  .filter('timePickerTime', () => timePickerTime)
  // Disable eslint react2angular-with-error-boundary rule
  // Rule fixer would cause a circular package dependency between utils and presentation
  // eslint-disable-next-line
  .component('systemTimezone', react2angular(SystemTimezone));
