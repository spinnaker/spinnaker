import { UrlBuilderRegistry } from '../navigation/UrlBuilderRegistry';
import { PipelineRegistry } from '../pipeline/config/PipelineRegistry';
import { DebugWindow } from '../utils/consoleDebug';

export class Registry {
  public static pipeline: PipelineRegistry = new PipelineRegistry();
  public static urlBuilder: UrlBuilderRegistry = new UrlBuilderRegistry();

  public static reinitialize = () => {
    Registry.pipeline = new PipelineRegistry();
    Registry.urlBuilder = new UrlBuilderRegistry();
  };
}

DebugWindow.Registry = Registry;
