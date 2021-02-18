import { module } from 'angular';
import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { react2angular } from 'react2angular';

import SlackChannelSelector from './SlackChannelSelector';

export const SLACK_COMPONENT = 'spinnaker.application.slackChannelSelector.component';

module(SLACK_COMPONENT, []).component(
  'slackChannelSelector',
  react2angular(withErrorBoundary(SlackChannelSelector, 'slackChannelSelector'), ['channel', 'callback']),
);
