import { $q } from 'ngimport';

import { REST } from '../../../api/ApiService';
import { IPipelineTemplateV2 } from '../../../domain/IPipelineTemplateV2';

export class PipelineTemplateWriter {
  public static savePipelineTemplateV2(template: IPipelineTemplateV2): PromiseLike<any> {
    return $q((resolve, reject) => {
      REST('/v2/pipelineTemplates/create').post(template).then(resolve, reject);
    });
  }

  public static deleteTemplate(template: { id: string; digest?: string; tag?: string }): PromiseLike<any> {
    let request = REST('/v2/pipelineTemplates').path(template.id);

    const params: { digest?: string; tag?: string } = {};
    if (template.digest) {
      params.digest = template.digest;
    } else if (template.tag) {
      params.tag = template.tag;
    }

    request = request.query(params);
    return request.delete();
  }
}
