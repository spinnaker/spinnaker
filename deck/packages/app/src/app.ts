/* eslint-disable @spinnaker/import-sort */
import 'rxjs-compat';

import '@spinnaker/docker';
import '@spinnaker/amazon';
import '@spinnaker/appengine';
import '@spinnaker/azure';
import '@spinnaker/google';
import './canary/canary.module';
import '@spinnaker/kubernetes';
import '@spinnaker/oracle';
import '@spinnaker/kayenta';
import '@spinnaker/titus';
import '@spinnaker/ecs';
import '@spinnaker/cloudrun';
import '@spinnaker/cloudfoundry';

import { bootstrapDeck } from '@spinnaker/core';

void bootstrapDeck(document.getElementById('spinnaker-root')).catch((error) => {
  console.error('Deck bootstrap failed', error);
});
