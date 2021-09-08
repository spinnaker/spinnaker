import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';
import { AlarmConfigurer } from './AlarmConfigurer';

export const AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARM_CONFIGURER_COMPONENT =
  'spinnaker.amazon.serverGroup.details.scalingPolicy.alarm.configurer.component';
export const name = AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARM_CONFIGURER_COMPONENT;

module(AMAZON_SERVERGROUP_DETAILS_SCALINGPOLICY_ALARM_CONFIGURER_COMPONENT, []).component(
  'alarmConfigurer',
  react2angular(withErrorBoundary(AlarmConfigurer, 'alarmConfigurer'), [
    'alarm',
    'multipleAlarms',
    'serverGroup',
    'stepAdjustments',
    'stepsChanged',
    'updateAlarm',
  ]),
);
