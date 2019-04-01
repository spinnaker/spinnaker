import { IPromise } from 'angular';
import { $q } from 'ngimport';
import { API } from 'core/api/ApiService';
import { IPipelineTemplateV2 } from 'core/domain/IPipelineTemplateV2';

export class PipelineTemplateWriter {
  public static savePipelineTemplateV2(template: IPipelineTemplateV2): IPromise<any> {
    return $q((resolve, reject) => {
      API.one('v2')
        .one('pipelineTemplates')
        .one('create')
        .post(template)
        .then(resolve, reject);
    });
  }

  public static deleteTemplate(template: { id: string; digest?: string; version?: string }): IPromise<any> {
    let request = API.one('v2')
      .one('pipelineTemplates')
      .one(template.id);

    const params: { digest?: string; version?: string } = {};
    if (template.digest) {
      params.digest = template.digest;
    } else if (template.version) {
      params.version = template.version;
    }

    request = request.withParams(params);
    return request.remove();
  }
}
