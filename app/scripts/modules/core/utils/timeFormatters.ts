import { IComponentController, IComponentOptions, module } from 'angular';
import * as moment from 'moment';

import { SETTINGS } from 'core/config/settings';

const isInputValid = (input: any) => !(!input || isNaN(input) || input < 0);

export function duration(input: any) {
  if (!isInputValid(input)) {
    return '-';
  }
  const thisMoment = moment.utc(parseInt(input, 10));
  const format = thisMoment.hours() ? 'HH:mm:ss' : 'mm:ss';
  let dayLabel = '';
  if (thisMoment.isValid()) {
    const days = moment.duration(input, 'milliseconds').days();
    if (days > 0) {
      dayLabel = days + 'd';
    }
  }
  return thisMoment.isValid() ? dayLabel + thisMoment.format(format) : '-';
}

export function timestamp(input: any) {
  if (!isInputValid(input)) {
    return '-';
  }
  const tz = SETTINGS.defaultTimeZone || 'America/Los_Angeles';
  const thisMoment = moment.tz(parseInt(input, 10), tz);
  return thisMoment.isValid() ? thisMoment.format('YYYY-MM-DD HH:mm:ss z') : '-';
}

export function relativeTime(input: any) {
  if (!isInputValid(input)) {
    return '-';
  }
  const thisMoment = moment(parseInt(input, 10));
  return thisMoment.isValid() ? thisMoment.fromNow() : '-';
}

export function fastPropertyTime(input: any) {
  if (input) {
    input = input.replace('[UTC]', '');
    const thisMoment = moment(input);
    return thisMoment.isValid() ? thisMoment.format('YYYY-MM-DD HH:mm:ss z') : '-';
  } else {
    return '--';
  }
}

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

export class SystemTimeZoneController implements IComponentController {
  public tz: string;
  constructor() {
    const zone = SETTINGS.defaultTimeZone || 'America/Los_Angeles';
    this.tz = moment.tz(new Date().getTime(), zone).zoneAbbr();
  }
}
export class SystemTimeZoneComponent implements IComponentOptions {
  public controller: any = SystemTimeZoneController;
  public template = `<span ng-bind="$ctrl.tz"></span>`;
}

export const TIME_FORMATTERS = 'spinnaker.core.utils.timeFormatters';
module(TIME_FORMATTERS, [])
  .filter('timestamp', () => timestamp)
  .filter('relativeTime', () => relativeTime)
  .filter('duration', () => duration)
  .filter('fastPropertyTime', () => fastPropertyTime)
  .filter('timePickerTime', () => timePickerTime)
  .component('systemTimezone', new SystemTimeZoneComponent());
