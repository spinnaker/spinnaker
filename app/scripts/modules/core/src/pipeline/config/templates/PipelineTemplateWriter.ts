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
}
