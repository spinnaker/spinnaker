import { module } from 'angular';
import { react2angular } from 'react2angular';

import SlackChannelSelector from './SlackChannelSelector';

export const SLACK_COMPONENT = 'spinnaker.application.slackChannelSelector.component';

module(SLACK_COMPONENT, []).component(
  'slackChannelSelector',
  react2angular(SlackChannelSelector, ['channel', 'callback']),
);
