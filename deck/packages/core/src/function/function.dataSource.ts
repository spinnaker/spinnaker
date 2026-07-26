import type { Application } from '../application/application.model';
import { INFRASTRUCTURE_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import { SETTINGS } from '../config/settings';
import type { IFunction, IFunctionSourceData } from '../domain';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';
import { FunctionReader } from './function.read.service';
import type { IFunctionTransformer } from './function.transformer';

export function createDirectFunctionReader(providerServiceDelegate: ProviderServiceDelegate): FunctionReader {
  const functionTransformer: IFunctionTransformer = {
    normalizeFunction: (functionDef: IFunctionSourceData) =>
      providerServiceDelegate
        .getDelegate<IFunctionTransformer>(functionDef.provider ? functionDef.provider : 'aws', 'function.transformer')
        .normalizeFunction(functionDef),
    normalizeFunctionSet: (functions: IFunctionSourceData[]) => {
      const setTransformers = functions
        .filter((fn) =>
          providerServiceDelegate.hasDelegate(fn.provider ? fn.provider : 'aws', 'function.setTransformer'),
        )
        .map((fn) =>
          providerServiceDelegate.getDelegate<IFunctionTransformer>(
            fn.provider ? fn.provider : 'aws',
            'function.setTransformer',
          ),
        )
        .filter(
          (transformer, index, transformers) =>
            transformers.findIndex(
              (candidate) => candidate.normalizeFunctionSet === transformer.normalizeFunctionSet,
            ) === index,
        );

      return setTransformers.reduce(
        (result, transformer) => transformer.normalizeFunctionSet.call(transformer, result),
        functions,
      );
    },
  };

  return new FunctionReader(functionTransformer);
}

export function registerFunctionDataSource(functionReader: FunctionReader): void {
  if (
    !SETTINGS.feature.functions ||
    ApplicationDataSourceRegistry.getDataSources().some((source) => source.key === 'functions')
  ) {
    return;
  }
  const functions = (application: Application) => {
    return functionReader.loadFunctions(application.name);
  };

  const addFunctions = (_application: Application, functionList: IFunction[]) => {
    return Promise.resolve(functionList);
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
    iconName: 'spMenuFunctions',
    loader: functions,
    onLoad: addFunctions,
    afterLoad: addTags,
    providerField: 'cloudProvider',
    credentialsField: 'account',
    regionField: 'region',
    description: 'Serverless Compute Service.',
    defaultData: [],
  });
}
