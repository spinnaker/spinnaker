import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE = 'spinnaker.core.pipeline.stage.runJobStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_RUNJOB_RUNJOBSTAGE; // for backwards compatibility

export const runJobStage = {
  useBaseProvider: true,
  key: 'runJob',
  label: 'Run Job',
  description: 'Runs a container',
  restartable: true,
};

Registry.pipeline.registerStage(runJobStage);
