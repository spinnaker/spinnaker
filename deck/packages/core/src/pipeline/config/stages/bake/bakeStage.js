import { ManualExecutionBake } from './ManualExecutionBake';
import { bakeStageTransformer } from './bakeStage.transformer';
import { NoConfigurationStageConfig } from '../common';
import { Registry } from '../../../../registry';

export const bakeStage = {
  useBaseProvider: true,
  label: 'Bake',
  description: 'Bakes an image',
  key: 'bake',
  component: NoConfigurationStageConfig,
  restartable: true,
  manualExecutionComponent: ManualExecutionBake,
};

Registry.pipeline.registerStage(bakeStage);
Registry.pipeline.registerTransformer(bakeStageTransformer);
