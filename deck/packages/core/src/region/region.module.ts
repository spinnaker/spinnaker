import { module } from 'angular';
import { CORE_REGION_REGIONSELECTFIELD_DIRECTIVE } from './regionSelectField.directive';

export const REGION_MODULE = 'spinnaker.core.region.module';
module(REGION_MODULE, [CORE_REGION_REGIONSELECTFIELD_DIRECTIVE]);
