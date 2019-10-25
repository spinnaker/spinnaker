import { module, IQService } from 'angular';

import { ApplicationDataSourceRegistry } from 'core/application/service/ApplicationDataSourceRegistry';
import { INFRASTRUCTURE_KEY } from 'core/application/nav/defaultCategories';
import { Application } from 'core/application/application.model';
import { EntityTagsReader } from 'core/entityTag/EntityTagsReader';
import { IFunction } from 'core/domain';
import { FUNCTION_READ_SERVICE, FunctionReader } from 'core/function/function.read.service';

export const FUNCTION_DATA_SOURCE = 'spinnaker.core.functions.dataSource';
module(FUNCTION_DATA_SOURCE, [FUNCTION_READ_SERVICE]).run([
  '$q',
  'functionReader',
  ($q: IQService, functionReader: FunctionReader) => {
    const functions = (application: Application) => {
      return functionReader.loadFunctions(application.name);
    };

    const addFunctions = (_application: Application, functionList: IFunction[]) => {
      return $q.when(functionList);
    };

    const addTags = (application: Application) => {
      EntityTagsReader.addTagsToFunctions(application);
    };

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'functions',
      label: 'functions',
      sref: '.insight.functions',
      category: INFRASTRUCTURE_KEY,
      optional: true,
      icon: 'fa fa-xs fa-fw icon-sitemap',
      loader: functions,
      onLoad: addFunctions,
      afterLoad: addTags,
      providerField: 'cloudProvider',
      credentialsField: 'account',
      regionField: 'region',
      description: 'Serverless Compute Service.',
    });
  },
]);
