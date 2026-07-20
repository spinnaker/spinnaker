import { module } from 'angular';

import SlackChannelSelector from './SlackChannelSelector';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const SLACK_COMPONENT = 'spinnaker.application.slackChannelSelector.component';

module(SLACK_COMPONENT, []).component(
  'slackChannelSelector',
  angularComponentFromReact(SlackChannelSelector, 'slackChannelSelector', ['channel', 'callback']),
);
