import { get } from 'lodash';

import { IPipeline, IStage, IStageOrTriggerValidator, ITrigger, PipelineConfigValidator } from '@spinnaker/core';

export class CfRequiredRoutesFieldValidator implements IStageOrTriggerValidator {
  public validate(_pipeline: IPipeline, stage: IStage | ITrigger, validationConfig: any): string {
    const routes: string[] = get(stage, validationConfig.fieldName);
    const routeErrors = routes
      .map((route: string) => {
        const regex = /^([-\w]+)\.([-.\w]+)(:\d+)?([-/\w]+)?$/gm;
        route = route || '';
        if (regex.exec(route) === null) {
          const spelRegex = /^\${.*}$/g;
          if (spelRegex.exec(route) === null) {
            return `"${route}" did not match the expected format "host.some.domain[:9999][/some/path]"`;
          }
        }
        return null;
      })
      .filter((err) => err != null);
    return (routeErrors && routeErrors.length && routeErrors[0]) || null;
  }
}

PipelineConfigValidator.registerValidator('cfRequiredRoutesField', new CfRequiredRoutesFieldValidator());
