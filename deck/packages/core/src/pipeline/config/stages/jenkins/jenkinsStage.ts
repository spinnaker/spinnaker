import { JenkinsExecutionDetails } from './JenkinsExecutionDetails';
import { JenkinsExecutionLabel } from './JenkinsExecutionLabel';
import { JenkinsStageConfig } from './JenkinsStageConfig';
import { ExecutionArtifactTab } from '../../../../artifact/react/ExecutionArtifactTab';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE = 'spinnaker.core.pipeline.stage.jenkinsStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_JENKINS_JENKINSSTAGE; // for backwards compatibility

export const jenkinsStage = {
  label: 'Jenkins',
  description: 'Runs a Jenkins job',
  key: 'jenkins',
  restartable: true,
  component: JenkinsStageConfig,
  producesArtifacts: true,
  executionDetailsSections: [JenkinsExecutionDetails, ExecutionDetailsTasks, ExecutionArtifactTab],
  executionLabelComponent: JenkinsExecutionLabel,
  providesVersionForBake: true,
  extraLabelLines: (stage: any) => {
    if (!stage.masterStage.context || !stage.masterStage.context.buildInfo) {
      return 0;
    }
    const lines = stage.masterStage.context.buildInfo.number ? 1 : 0;
    return lines + (stage.masterStage.context.buildInfo.testResults || []).length;
  },
  supportsCustomTimeout: true,
  validators: [{ type: 'requiredField', fieldName: 'job' }],
  strategy: true,
};

Registry.pipeline.registerStage(jenkinsStage);
