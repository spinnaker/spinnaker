import { ManualExecutionBake } from './ManualExecutionBake';
import { bakeStageTransformer } from './bakeStage.transformer';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE = 'spinnaker.core.pipeline.stage.bakeStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE; // for backwards compatibility

export const bakeStage = {
  useBaseProvider: true,
  label: 'Bake',
  description: 'Bakes an image',
  key: 'bake',
  restartable: true,
  manualExecutionComponent: ManualExecutionBake,
};

Registry.pipeline.registerStage(bakeStage);
Registry.pipeline.registerTransformer(bakeStageTransformer);
