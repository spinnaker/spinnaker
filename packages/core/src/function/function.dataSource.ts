import { IQService, module } from 'angular';
import { Application } from '../application/application.model';
import { INFRASTRUCTURE_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { SETTINGS } from '../config/settings';
import { IFunction } from '../domain';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';

import { FUNCTION_READ_SERVICE, FunctionReader } from './function.read.service';

export const FUNCTION_DATA_SOURCE = 'spinnaker.core.functions.dataSource';
module(FUNCTION_DATA_SOURCE, [FUNCTION_READ_SERVICE]).run([
  '$q',
  'functionReader',
  ($q: IQService, functionReader: FunctionReader) => {
    if (!SETTINGS.feature.functions) {
      return;
    }
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
      icon: 'fa fa-xs fa-fw fa-asterisk',
      loader: functions,
      onLoad: addFunctions,
      afterLoad: addTags,
      providerField: 'cloudProvider',
      credentialsField: 'account',
      regionField: 'region',
      description: 'Serverless Compute Service.',
      defaultData: [],
    });
  },
]);
