'use strict';

const angular = require('angular');

import { RUN_AS_USER_SELECTOR_COMPONENT } from './runAsUserSelector.component';
import { TRAVIS_TRIGGER } from './travis/travisTrigger.module';
import { WERCKER_TRIGGER } from './wercker/werckerTrigger.module';
import { GIT_TRIGGER } from './git/git.trigger';
import { PUBSUB_TRIGGER } from './pubsub/pubsub.trigger';
import { WEBHOOK_TRIGGER } from './webhook/webhook.trigger';
import { ARTIFACT_MODULE } from './artifacts/artifact.module';
import { PIPELINE_ROLES_COMPONENT } from './pipelineRoles.component';

module.exports = angular.module('spinnaker.core.pipeline.config.trigger', [
  ARTIFACT_MODULE,
  require('../stages/stage.module.js').name,
  require('./cron/cronTrigger.module.js').name,
  GIT_TRIGGER,
  require('./jenkins/jenkinsTrigger.module.js').name,
  WERCKER_TRIGGER,
  TRAVIS_TRIGGER,
  require('./pipeline/pipelineTrigger.module.js').name,
  PUBSUB_TRIGGER,
  WEBHOOK_TRIGGER,
  require('./trigger.directive.js').name,
  require('./triggers.directive.js').name,
  RUN_AS_USER_SELECTOR_COMPONENT,
  PIPELINE_ROLES_COMPONENT,
]);
