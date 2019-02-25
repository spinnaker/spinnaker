import { module } from 'angular';
import { DateTime, Duration } from 'luxon';
import { memoize, MemoizedFunction } from 'lodash';
import * as distanceInWordsToNow from 'date-fns/distance_in_words_to_now';
import { react2angular } from 'react2angular';

import { SETTINGS } from 'core/config/settings';

import { SystemTimezone } from './SystemTimezone';

const isInputValid = (input: any) => !(!input || isNaN(input) || input < 0);

export function duration(input: any) {
  if (!isInputValid(input)) {
    return '-';
  }
  // formatting does not support optionally omitting fields so we have to get
  // a little weird with the format strings and the durations we send into them
  const baseDuration = Duration.fromMillis(parseInt(input, 10));
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

export function timestamp(input: any) {
  if (!isInputValid(input)) {
    return '-';
  }
  const tz = SETTINGS.feature.displayTimestampsInUserLocalTime ? undefined : SETTINGS.defaultTimeZone;
  const thisMoment = DateTime.fromMillis(parseInt(input, 10), { zone: tz });
  return thisMoment.isValid ? thisMoment.toFormat('yyyy-MM-dd HH:mm:ss ZZZZ') : '-';
}

export function relativeTime(input: any) {
  if (!isInputValid(input)) {
    return '-';
  }
  const now = Date.now();
  const inputNumber = parseInt(input, 10);
  const inFuture = inputNumber > now;
  const thisMoment = DateTime.fromMillis(inputNumber);
  const baseText = distanceInWordsToNow(thisMoment.toJSDate(), { includeSeconds: true });
  return thisMoment.isValid ? `${inFuture ? 'in ' : ''}${baseText}${inFuture ? '' : ' ago'}` : '-';
}

export const fastPropertyTime: ((input: any) => string) & MemoizedFunction = memoize((input: any) => {
  if (input) {
    input = input.replace('[UTC]', '');
    const thisMoment = DateTime.fromMillis(input);
    return thisMoment.isValid ? thisMoment.toFormat('yyyy-MM-dd HH:mm:ss ZZZZ') : '-';
  } else {
    return '--';
  }
});

export const fastPropertyTtl = (input: any, seconds: number) => {
  if (input) {
    input = input.replace('[UTC]', '');
    const thisMoment = DateTime.fromMillis(input + seconds * 1000);
    return thisMoment.isValid ? thisMoment.toFormat('yyyy-MM-dd HH:mm:ss ZZZZ') : '-';
  } else {
    return '--';
  }
};

export function timePickerTime(input: any) {
  if (input && !isNaN(input.hours) && !isNaN(input.minutes)) {
    const hours = parseInt(input.hours, 10),
      minutes = parseInt(input.minutes, 10);

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
  .filter('fastPropertyTime', () => fastPropertyTime)
  .filter('timePickerTime', () => timePickerTime)
  .component('systemTimezone', react2angular(SystemTimezone));
