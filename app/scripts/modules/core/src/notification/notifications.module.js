'use strict';

import './selector/types/bearychat/beary.notification';
import './selector/types/email/email.notification';
import './selector/types/githubstatus/githubstatus.notification';
import './selector/types/googlechat/googlechat.notification';
import './selector/types/pubsub/pubsub.notification';
import './selector/types/slack/slack.notification';
import './selector/types/sms/sms.notification';
import { NOTIFICATION_LIST } from './notificationList.module';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.notifications', [NOTIFICATION_LIST]);
