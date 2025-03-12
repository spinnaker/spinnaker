///<reference path="angulartics.module.d.ts"/>
import { module } from 'angular';
import angulartics from 'angulartics';
import angularticsGoogleAnalytics from 'angulartics-google-analytics';

export const ANALYTICS_MODULE = 'spinnaker.core.analytics';
module(ANALYTICS_MODULE, [angulartics, angularticsGoogleAnalytics]);
