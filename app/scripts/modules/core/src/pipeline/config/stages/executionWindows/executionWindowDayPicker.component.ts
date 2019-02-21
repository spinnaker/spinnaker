import { IController, module, noop } from 'angular';

import { DAYS_OF_WEEK } from './daysOfWeek';

interface IWindowConfig {
  days: number[];
}

class ExecutionWindowDayPickerController implements IController {
  public DAYS_OF_WEEK: any = DAYS_OF_WEEK;
  public windowConfig: IWindowConfig;
  public onChange: () => void;

  public $onInit(): void {
    if (!this.onChange) {
      this.onChange = noop;
    }
  }

  public daySelected(ordinal: number): boolean {
    if (!this.windowConfig.days) {
      return false;
    }
    return this.windowConfig.days.includes(ordinal);
  }

  public all(): void {
    this.windowConfig.days = [1, 2, 3, 4, 5, 6, 7];
    this.onChange();
  }

  public none(): void {
    this.windowConfig.days = [];
    this.onChange();
  }

  public weekdays(): void {
    this.windowConfig.days = [2, 3, 4, 5, 6];
    this.onChange();
  }

  public weekend(): void {
    this.windowConfig.days = [1, 7];
    this.onChange();
  }

  public updateModel(day: any): void {
    if (!this.windowConfig.days) {
      this.windowConfig.days = [];
    }
    if (this.windowConfig.days.includes(day.ordinal)) {
      this.windowConfig.days.splice(this.windowConfig.days.indexOf(day.ordinal), 1);
    } else {
      this.windowConfig.days.push(day.ordinal);
    }
    this.onChange();
  }
}

export const EXECUTION_WINDOWS_DAY_PICKER = 'spinnaker.core.pipeline.stage.executionWindows.dayPicker';

const executionWindowDayPickerComponent: ng.IComponentOptions = {
  bindings: {
    windowConfig: '<',
    onChange: '&',
  },
  controller: ExecutionWindowDayPickerController,
  templateUrl: require('./executionWindowDayPicker.component.html')
};

module(EXECUTION_WINDOWS_DAY_PICKER, []).component('executionWindowDayPicker', executionWindowDayPickerComponent);
