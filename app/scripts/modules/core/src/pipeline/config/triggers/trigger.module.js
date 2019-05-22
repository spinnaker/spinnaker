'use strict';

const angular = require('angular');

import './artifactory/artifactory.trigger';
import './concourse/concourse.trigger';
import './cron/cron.trigger';
import './git/git.trigger';
import './jenkins/jenkins.trigger';
import './pubsub/pubsub.trigger';
import './pipeline/pipeline.trigger';
import './travis/travis.trigger';
import './webhook/webhook.trigger';
import './wercker/wercker.trigger';
import { ARTIFACT_MODULE } from './artifacts/artifact.module';
import { PIPELINE_ROLES_COMPONENT } from './pipelineRoles.component';

module.exports = angular.module('spinnaker.core.pipeline.config.trigger', [
  ARTIFACT_MODULE,
  require('../stages/stage.module').name,
  require('./trigger.directive').name,
  require('./triggers.directive').name,
  PIPELINE_ROLES_COMPONENT,
]);
