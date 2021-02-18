import { ArtifactReferenceService, ExpectedArtifactService } from 'core/artifact';
import { ExecutionArtifactTab } from 'core/artifact/react/ExecutionArtifactTab';
import { Registry } from 'core/registry';

import { SavePipelinesResultsTab } from './SavePipelinesResultsTab';
import { SavePipelinesStageConfig } from './SavePipelinesStageConfig';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';

Registry.pipeline.registerStage({
  label: 'Save Pipelines',
  description: 'Saves pipelines defined in an artifact.',
  key: 'savePipelinesFromArtifact',
  component: SavePipelinesStageConfig,
  executionDetailsSections: [ExecutionDetailsTasks, ExecutionArtifactTab, SavePipelinesResultsTab],
  supportsCustomTimeout: true,
  artifactExtractor: ExpectedArtifactService.accumulateArtifacts(['pipelinesArtifactId', 'requiredArtifactIds']),
  artifactRemover: ArtifactReferenceService.removeArtifactFromFields(['pipelinesArtifactId', 'requiredArtifactIds']),
});
