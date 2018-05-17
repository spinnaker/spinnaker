import { PipelineRegistry } from 'core/pipeline/config/PipelineRegistry';

export class Registry {
  public static pipeline: PipelineRegistry;

  public static initialize = () => {
    Registry.pipeline = new PipelineRegistry();
  };
}
