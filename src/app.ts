import { module } from 'angular';

import { CORE_MODULE } from '@spinnaker/core';
import { AMAZON_MODULE } from '@spinnaker/amazon';
import { GOOGLE_MODULE } from '@spinnaker/google';
import { KAYENTA_MODULE } from './kayenta/canary.module';

module('netflix.spinnaker', [
  CORE_MODULE,
  AMAZON_MODULE,
  GOOGLE_MODULE,
  KAYENTA_MODULE,
]);
