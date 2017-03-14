import {IPipeline, IParameter} from 'core/domain/IPipeline';
import {IStage} from 'core/domain/IStage';
import {ITrigger} from '../../../core/domain/ITrigger';

export class PropertyPipeline implements IPipeline {
  public strategy: boolean;
  public parameterConfig: IParameter[];
  public application: string;
  public index: number;
  public keepWaitingPipelines: boolean;
  public lastModifiedBy: string;
  public limitConcurrent: boolean;
  public name: string;
  public parallel: boolean;
  public triggers: ITrigger[];
  public stages: IStage[];
  public executionEngine = 'v2';
  public id: string;
  public updateTs = Date.now();

  constructor(pipelineConfigId: string) {
    this.id = pipelineConfigId;
  }
}
