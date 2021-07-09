import { range } from 'lodash';

export interface ITimePickerOption {
  label: string;
  value: number;
}

export class TimePickerOptions {
  public static getHours() {
    const hours: ITimePickerOption[] = [];
    range(0, 10).forEach((hour) => {
      hours.push({ label: '0' + hour, value: hour });
    });
    range(10, 24).forEach((hour) => {
      hours.push({ label: '' + hour, value: hour });
    });
    return hours;
  }

  public static getMinutes() {
    const minutes: ITimePickerOption[] = [];
    range(0, 10).forEach((minute) => {
      minutes.push({ label: '0' + minute, value: minute });
    });
    range(10, 60).forEach((minute) => {
      minutes.push({ label: '' + minute, value: minute });
    });
    return minutes;
  }
}
