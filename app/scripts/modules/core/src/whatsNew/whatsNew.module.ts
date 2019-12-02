import { module } from 'angular';
import { CORE_WHATSNEW_WHATSNEW_DIRECTIVE } from './whatsNew.directive';

export const WHATS_NEW_MODULE = 'spinnaker.core.whatsNew';
module(WHATS_NEW_MODULE, [CORE_WHATSNEW_WHATSNEW_DIRECTIVE]);
