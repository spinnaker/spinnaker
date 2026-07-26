import { RequestBuilder } from '../../../../api/ApiService';
import type { IHttpClientImplementation } from '../../../../api/ApiService';
import { Registry } from '../../../../registry';
import { webhookExecutionDetailsSections } from './WebhookExecutionDetails';
import * as webhookStageModule from './webhookStage';

describe('Webhook stage registration', () => {
  beforeEach(() => Registry.reinitialize());

  it('builds the Webhook stage as a React stage config', () => {
    const { WebhookStageConfig, webhookStage } = webhookStageModule as any;

    expect(webhookStage).toEqual(
      jasmine.objectContaining({
        label: 'Webhook',
        description: 'Runs a Webhook job',
        key: 'webhook',
        restartable: true,
        producesArtifacts: true,
        component: WebhookStageConfig,
        executionDetailsSections: webhookExecutionDetailsSections,
        supportsCustomTimeout: true,
        validators: [
          { type: 'requiredField', fieldName: 'url' },
          { type: 'requiredField', fieldName: 'method' },
        ],
      }),
    );
    expect(webhookStage.templateUrl).toBeUndefined();
    expect(webhookStage.controller).toBeUndefined();
    expect(webhookStage.controllerAs).toBeUndefined();
  });

  it('builds preconfigured webhook stages as React stage configs', () => {
    const { WebhookStageConfig, makePreconfiguredWebhookStage } = webhookStageModule as any;

    const stage = makePreconfiguredWebhookStage({
      type: 'preconfiguredWebhookType',
      label: 'Preconfigured Webhook',
      description: 'From Gate',
      preconfiguredProperties: ['url', 'method'],
      waitForCompletion: true,
      noUserConfigurableFields: false,
      parameters: [{ name: 'target', label: 'Target', type: 'string', defaultValue: 'prod' }],
    });

    expect(stage).toEqual(
      jasmine.objectContaining({
        label: 'Preconfigured Webhook',
        description: 'From Gate',
        key: 'preconfiguredWebhookType',
        alias: 'preconfiguredWebhook',
        addAliasToConfig: true,
        producesArtifacts: true,
        restartable: true,
        component: WebhookStageConfig,
        executionDetailsSections: webhookExecutionDetailsSections,
        validators: [],
        configuration: {
          preconfiguredProperties: ['url', 'method'],
          waitForCompletion: true,
          noUserConfigurableFields: false,
          parameters: [{ name: 'target', label: 'Target', type: 'string', defaultValue: 'prod' }],
        },
      }),
    );
    expect(stage.templateUrl).toBeUndefined();
    expect(stage.controller).toBeUndefined();
    expect(stage.controllerAs).toBeUndefined();
  });

  it('fetches and registers preconfigured webhooks', async () => {
    const { WebhookStageConfig, registerPreconfiguredWebhookStages } = webhookStageModule as any;
    const originalHttpClient = RequestBuilder.defaultHttpClient;
    const get = jasmine.createSpy('get').and.returnValue(
      Promise.resolve([
        {
          type: 'dynamicWebhook',
          label: 'Dynamic Webhook',
          noUserConfigurableFields: false,
        },
      ]),
    );
    RequestBuilder.defaultHttpClient = { get } as IHttpClientImplementation;
    const registerSpy = spyOn(Registry.pipeline, 'registerStage').and.callThrough();

    try {
      await registerPreconfiguredWebhookStages();

      expect(get).toHaveBeenCalledWith(
        jasmine.objectContaining({ url: jasmine.stringMatching(/webhooks\/preconfigured$/) }),
      );
      expect(registerSpy).toHaveBeenCalledWith(
        jasmine.objectContaining({
          key: 'dynamicWebhook',
          component: WebhookStageConfig,
          alias: 'preconfiguredWebhook',
        }),
      );
    } finally {
      RequestBuilder.defaultHttpClient = originalHttpClient;
    }
  });
});
