///<reference path="angulartics.module.d.ts"/>
import { module } from 'angular';
import * as angulartics from 'angulartics';
import * as angularticsGoogleAnalytics from 'angulartics-google-analytics';

export const ANALYTICS_MODULE = 'spinnaker.core.analytics';
module(ANALYTICS_MODULE, [angulartics, angularticsGoogleAnalytics]);
