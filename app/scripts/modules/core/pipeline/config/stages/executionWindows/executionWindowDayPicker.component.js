const angular = require('angular');

import { DAYS_OF_WEEK } from './daysOfWeek';

class ExecutionWindowDayPickerController {

  constructor() {
    this.DAYS_OF_WEEK = DAYS_OF_WEEK;
  }

  daySelected(ordinal) {
    const days = new Set(this.days);
    return days.has(ordinal);
  }

  all() {
    this.days = [1, 2, 3, 4, 5, 6, 7];
  }

  none() {
    this.days = [];
  }

  weekdays() {
    this.days = [2, 3, 4, 5, 6];
  }

  weekend() {
    this.days = [1, 7];
  }

  updateModel(day) {

    if (!this.days) {
      this.days = []; // for pre-existing pipelines, the 'days' property will not exist
    }

    const days = new Set(this.days);
    if (days.has(day.ordinal)) {
      this.days = this.days.filter((_day) => _day !== day.ordinal);
    }
    else {
      this.days.push(day.ordinal);
    }
  }
}

module.exports = angular.module('spinnaker.core.pipeline.stage.executionWindows.dayPicker', [])
  .component('executionWindowDayPicker', {
    bindings: {
      days: '='
    },
    controller: ExecutionWindowDayPickerController,
    templateUrl: require('./executionWindowDayPicker.component.html')
  });
