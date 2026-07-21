import { module } from 'angular';

import { angularComponentFromReact } from '../angular/angularComponentFromReact';

import { ExecutionArtifactTab } from './react/ExecutionArtifactTab';

export const EXECUTION_ARTIFACT_TAB = 'spinnaker.core.artifact.artifactTab.component';

module(EXECUTION_ARTIFACT_TAB, []).component(
  'executionArtifactTab',
  angularComponentFromReact(ExecutionArtifactTab, 'executionArtifactTab', ['config', 'stage', 'execution']),
);
