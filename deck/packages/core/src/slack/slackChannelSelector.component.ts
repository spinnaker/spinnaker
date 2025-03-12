import { module } from 'angular';
import { react2angular } from 'react2angular';

import SlackChannelSelector from './SlackChannelSelector';
import { withErrorBoundary } from '../presentation/SpinErrorBoundary';

export const SLACK_COMPONENT = 'spinnaker.application.slackChannelSelector.component';

module(SLACK_COMPONENT, []).component(
  'slackChannelSelector',
  react2angular(withErrorBoundary(SlackChannelSelector, 'slackChannelSelector'), ['channel', 'callback']),
);
