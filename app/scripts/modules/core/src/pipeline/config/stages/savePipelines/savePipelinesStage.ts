import { SavePipelinesResultsTab } from './SavePipelinesResultsTab';
import { SavePipelinesStageConfig } from './SavePipelinesStageConfig';
import { ArtifactReferenceService, ExpectedArtifactService } from '../../../../artifact';
import { ExecutionArtifactTab } from '../../../../artifact/react/ExecutionArtifactTab';
import { ExecutionDetailsTasks } from '../common/ExecutionDetailsTasks';
import { Registry } from '../../../../registry';

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
