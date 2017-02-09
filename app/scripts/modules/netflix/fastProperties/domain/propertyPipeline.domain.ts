import {IPipeline, IParameter} from 'core/domain/IPipeline';
import {IStage} from 'core/domain/IStage';
import {ITrigger} from '../../../core/domain/ITrigger';

export class PropertyPipeline implements IPipeline {
  strategy: boolean;
  parameterConfig: IParameter[];
  application: string;
  index: number;
  keepWaitingPipelines: boolean;
  lastModifiedBy: string;
  limitConcurrent: boolean;
  name: string;
  parallel: boolean;
  triggers: ITrigger[];
  stages: IStage[];
  executionEngine: string = 'v2';
  id: string;
  updateTs = Date.now();

  constructor(pipelineConfigId: string) {
    this.id = pipelineConfigId;
  }
}
