import { PipelineRegistry } from 'core/pipeline/config/PipelineRegistry';

export class Registry {
  public static pipeline: PipelineRegistry = new PipelineRegistry();

  public static reinitialize = () => {
    Registry.pipeline = new PipelineRegistry();
  };
}
