import { module } from 'angular';

import { TITUS_CREATE_SCALING_POLICY_BUTTON } from './createScalingPolicyButton.component';
import { TITUS_SERVERGROUP_CUSTOM_SCALING_COMPONENT } from './titusCustomScalingPolicy.component';

export const SCALING_POLICY_MODULE = 'spinnaker.titus.scalingPolicy.module';
module(SCALING_POLICY_MODULE, [TITUS_CREATE_SCALING_POLICY_BUTTON, TITUS_SERVERGROUP_CUSTOM_SCALING_COMPONENT]);
