'use strict';

const angular = require('angular');

import {later} from 'core/utils/later/later';
import {SETTINGS} from 'core/config/settings';

module.exports = angular
  .module('spinnaker.core.deliver.triggers.nextRun', [
    require('../../utils/moment')
  ])
  .component('nextRunTag', {
    bindings: {
      pipeline: '<'
    },
    controller: function (momentService) {

      this.updateSchedule = () => {
        if (!this.pipeline) {
          return;
        }
        let crons = (this.pipeline.triggers || []).filter(t => t.type === 'cron' && t.enabled);
        let nextTimes = [];
        crons.forEach(cron => {
          let parts = cron.cronExpression.split(' ');
          let hours = parts[2];
          if (!isNaN(parseInt(hours))) {
            let allHours = hours.split('/');
            let tz = SETTINGS.defaultTimeZone || 'America/Los_Angeles';
            var offset = momentService.tz.zone(tz).offset(new Date());
            if (offset) {
              offset /= 60;
              let start = parseInt(allHours[0]);
              allHours[0] = (start + offset) % 24;
              parts[2] = allHours.join('/');
            }
          }
          let schedule = later.parse.cron(parts.join(' '), true);
          let nextRun = later.schedule(schedule).next(1);
          if (nextRun) {
            nextTimes.push(later.schedule(schedule).next(1).getTime());
          }
        });
        if (nextTimes.length) {
          this.hasNextScheduled = true;
          this.nextScheduled = Math.min(...nextTimes);
        }
      };

      this.$onInit = this.updateSchedule;

      this.getNextDuration = () => momentService(this.nextScheduled).fromNow();
    },
    template: `
      <span is-visible="$ctrl.hasNextScheduled">
        <span class="glyphicon glyphicon-time"
              popover-placement="left"
              popover-trigger="'mouseenter'"
              ng-mouseenter="$ctrl.updateSchedule()"
              uib-popover="Next run: {{$ctrl.nextScheduled | timestamp}} ({{$ctrl.getNextDuration()}})"></span>
      </span>`
  });
