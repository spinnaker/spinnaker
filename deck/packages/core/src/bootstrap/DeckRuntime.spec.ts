import { RejectType } from '@uirouter/core';
import { UIRouterReact } from '@uirouter/react';

import type { DeckRuntime } from './DeckRuntime';
import { createDeckRuntime } from './DeckRuntime';
import { DeckRuntimeContext } from './DeckRuntimeContext';
import type { RuntimePageTitleConfig, RuntimePageTitleService } from './DeckRuntimeServices';
import { CloudProviderRegistry } from '../cloudProvider';
import { SETTINGS } from '../config';

describe('createDeckRuntime', () => {
  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

  it('assembles direct runtime dependencies', () => {
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);

    expect(runtime.router).toBe(router);
    expect(runtime.promiseService).toEqual({
      all: jasmine.any(Function),
      reject: jasmine.any(Function),
      resolve: jasmine.any(Function),
    });
    expect(runtime.timeoutService).toEqual(jasmine.any(Function));
    expect(runtime.logger.error).toEqual(jasmine.any(Function));
    expect(runtime.interpolate).toEqual(jasmine.any(Function));
    expect(runtime.services.providerServiceDelegate).toBeDefined();
    expect(runtime.routingState.routing).toBe(false);
    expect(DeckRuntimeContext._currentValue).toBeNull();
    router.dispose();
  });

  it('cancels pending runtime work when disposed', () => {
    const runtime = createDeckRuntime();
    const callback = jasmine.createSpy('callback');
    const disposalOrder: string[] = [];
    const actualDisposeRoutingState = runtime.routingState.dispose.bind(runtime.routingState);
    const actualDisposeTimeoutService = runtime.timeoutService.dispose.bind(runtime.timeoutService);
    const disposeRoutingState = spyOn(runtime.routingState, 'dispose').and.callFake(() => {
      disposalOrder.push('routing state');
      actualDisposeRoutingState();
    });
    spyOn(runtime.timeoutService, 'dispose').and.callFake(() => {
      disposalOrder.push('timeout service');
      actualDisposeTimeoutService();
    });
    runtime.timeoutService(callback, 100);

    runtime.dispose();
    jasmine.clock().tick(100);

    expect(callback).not.toHaveBeenCalled();
    expect(disposeRoutingState).toHaveBeenCalledTimes(1);
    expect(disposalOrder).toEqual(['routing state', 'timeout service']);
  });

  it('creates services lazily and preserves their identity within a runtime', () => {
    const runtime = createDeckRuntime();

    expect(() => runtime.services.executionService).toThrowError(
      'Cannot create ExecutionService before the direct UI Router is initialized',
    );

    expect(runtime.services.cacheInitializer).toBe(runtime.services.cacheInitializer);
    expect(runtime.services.loadBalancerReader).toBe(runtime.services.loadBalancerReader);
    expect(runtime.services.serverGroupWriter).toBe(runtime.services.serverGroupWriter);
  });

  it('accepts optional direct and router-wrapped page title configs', () => {
    const runtime = createDeckRuntime();
    const pageTitleService: RuntimePageTitleService = runtime.services.pageTitleService;
    const compatibleConfigs: RuntimePageTitleConfig[] = [
      {},
      { pageTitleMain: {} },
      { pageTitleMain: { field: 'missingPageTitleField' } },
      { pageTitleMain: { label: 'Search' } },
      {
        data: {
          pageTitleMain: { label: 'Application' },
          pageTitleSection: { title: 'Pipelines' },
          pageTitleDetails: { title: 'Execution' },
        },
      },
    ];

    pageTitleService.handleRoutingSuccess();
    expect(document.title).toBe('Spinnaker');

    pageTitleService.handleRoutingSuccess(compatibleConfigs[0]);
    expect(document.title).toBe('Spinnaker');

    pageTitleService.handleRoutingSuccess(compatibleConfigs[1]);
    expect(document.title).toBe('Spinnaker');

    pageTitleService.handleRoutingSuccess(compatibleConfigs[2]);
    expect(document.title).toBe('Spinnaker');

    pageTitleService.handleRoutingSuccess(compatibleConfigs[3]);
    expect(document.title).toBe('Search');

    pageTitleService.handleRoutingSuccess(compatibleConfigs[4]);
    expect(document.title).toBe('Application · Pipelines · Execution');

    runtime.dispose();
  });

  it('shows a loading title while a direct route is pending', () => {
    const runtime = createDeckRuntime();
    const pageTitleService = runtime.services.pageTitleService;
    document.title = 'Previous title';

    pageTitleService.handleRoutingStart({ id: 'pending' });

    expect(document.title).toBe('Spinnaker: Loading...');
    runtime.dispose();
  });

  it('shows an error title for an ordinary direct route failure', () => {
    const runtime = createDeckRuntime();
    const pageTitleService = runtime.services.pageTitleService;
    const transition = { id: 'failed' };
    document.title = 'Previous title';

    pageTitleService.handleRoutingStart(transition);
    pageTitleService.handleRoutingError({ type: RejectType.ERROR }, transition);

    expect(document.title).toBe('Spinnaker: Error');
    runtime.dispose();
  });

  it('restores the prior title when a direct route aborts', () => {
    const runtime = createDeckRuntime();
    const pageTitleService = runtime.services.pageTitleService;
    const transition = { id: 'aborted' };
    document.title = 'Previous title';

    pageTitleService.handleRoutingStart(transition);
    pageTitleService.handleRoutingError({ type: RejectType.ABORTED }, transition);

    expect(document.title).toBe('Previous title');
    runtime.dispose();
  });

  it('lets only the newest overlapping direct route update the title', () => {
    const runtime = createDeckRuntime();
    const pageTitleService = runtime.services.pageTitleService;
    const older = { id: 'older' };
    const replacement = { id: 'replacement' };
    document.title = 'Previous title';

    pageTitleService.handleRoutingStart(older);
    pageTitleService.handleRoutingStart(replacement);
    pageTitleService.handleRoutingError({ type: RejectType.SUPERSEDED }, older);
    expect(document.title).toBe('Spinnaker: Loading...');

    pageTitleService.handleRoutingSuccess({ pageTitleMain: { label: 'Replacement' } }, replacement);
    pageTitleService.handleRoutingSuccess({ pageTitleMain: { label: 'Older' } }, older);
    pageTitleService.handleRoutingError({ type: RejectType.ERROR }, older);

    expect(document.title).toBe('Replacement');
    runtime.dispose();
  });

  it('ignores transition-less title updates while a direct route is active', () => {
    const runtime = createDeckRuntime();
    const pageTitleService = runtime.services.pageTitleService;
    const transition = { id: 'active' };
    document.title = 'Previous title';

    pageTitleService.handleRoutingStart(transition);
    pageTitleService.handleRoutingSuccess({ pageTitleMain: { label: 'Stale dynamic title' } });

    expect(document.title).toBe('Spinnaker: Loading...');
    pageTitleService.handleRoutingSuccess({ pageTitleMain: { label: 'Destination' } }, transition);
    expect(document.title).toBe('Destination');
    runtime.dispose();
  });

  it('ignores late direct route settlements after service disposal', () => {
    const runtime = createDeckRuntime();
    const pageTitleService = runtime.services.pageTitleService;
    const transition = { id: 'disposed' };
    document.title = 'Previous title';
    pageTitleService.handleRoutingStart(transition);

    pageTitleService.dispose();
    pageTitleService.handleRoutingSuccess({ pageTitleMain: { label: 'Late success' } }, transition);
    pageTitleService.handleRoutingError({ type: RejectType.ERROR }, transition);

    expect(document.title).toBe('Spinnaker: Loading...');
    runtime.dispose();
  });

  it('does not reactivate the page title service after runtime disposal', () => {
    const runtime = createDeckRuntime();
    const pageTitleService = runtime.services.pageTitleService;
    document.title = 'Title before disposal';

    runtime.dispose();
    const serviceAfterDisposal = runtime.services.pageTitleService;
    serviceAfterDisposal.handleRoutingSuccess({ pageTitleMain: { label: 'Late title' } });

    expect(serviceAfterDisposal).toBe(pageTitleService);
    expect(document.title).toBe('Title before disposal');
  });

  it('isolates service instances between runtimes', () => {
    const firstRuntime = createDeckRuntime();
    const secondRuntime = createDeckRuntime();

    expect(firstRuntime.services.cacheInitializer).not.toBe(secondRuntime.services.cacheInitializer);
    expect(firstRuntime.services.serverGroupTransformer).not.toBe(secondRuntime.services.serverGroupTransformer);
    expect(firstRuntime.routingState).not.toBe(secondRuntime.routingState);
  });

  it('releases service instances when disposed', () => {
    const runtime = createDeckRuntime();
    const cacheInitializer = runtime.services.cacheInitializer;
    const serverGroupWriter = runtime.services.serverGroupWriter;

    runtime.dispose();

    expect(runtime.services.cacheInitializer).not.toBe(cacheInitializer);
    expect(runtime.services.serverGroupWriter).not.toBe(serverGroupWriter);
  });

  describe('direct runtime service wiring', () => {
    const provider = 'deckRuntimeServiceTest';
    let router: UIRouterReact;
    let runtime: DeckRuntime;

    beforeEach(() => {
      SETTINGS.providers[provider] = { enabled: true };
      router = new UIRouterReact();
      runtime = createDeckRuntime(router);
    });

    afterEach(() => {
      runtime.dispose();
      router.dispose();
      delete SETTINGS.providers[provider];
      (CloudProviderRegistry as any).providers.delete(provider);
    });

    it('constructs an infrastructure search service', () => {
      expect(runtime.services.infrastructureSearchService.getSearcher()).toBeDefined();
    });

    it('constructs an execution details section service for the runtime router', () => {
      expect(() => runtime.services.executionDetailsSectionService.synchronizeSection(['stage'])).not.toThrow();
    });

    it('constructs a security group reader', () => {
      expect(runtime.services.securityGroupReader.getAllSecurityGroups).toEqual(jasmine.any(Function));
    });

    it('constructs an instance type service', () => {
      expect(runtime.services.instanceTypeService.getCategoryForMultipleInstanceTypes).toEqual(jasmine.any(Function));
    });

    it('builds server group commands with the registered provider command builder', async () => {
      const stage = { type: 'deploy' };
      const pipeline = { id: 'pipeline-1' };

      class TestServerGroupCommandBuilder {
        public buildNewServerGroupCommandForPipeline(currentStage: any, currentPipeline: any) {
          return Promise.resolve({ currentStage, currentPipeline });
        }
      }

      CloudProviderRegistry.registerProvider(provider, {
        name: 'Test Provider',
        serverGroup: { commandBuilder: TestServerGroupCommandBuilder },
      });

      await expectAsync(
        runtime.services.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(provider, stage, pipeline),
      ).toBeResolvedTo({ currentStage: stage, currentPipeline: pipeline });
    });

    it('converts server group commands with the registered provider transformer', () => {
      const command = { selectedProvider: provider, cluster: 'ecsapp-prod-ecsdemo' };

      class TestServerGroupTransformer {
        public convertServerGroupCommandToDeployConfiguration(base: any) {
          return { converted: true, base };
        }
      }

      CloudProviderRegistry.registerProvider(provider, {
        name: 'Test Provider',
        serverGroup: { transformer: TestServerGroupTransformer },
      });

      expect(runtime.services.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command)).toEqual({
        converted: true,
        base: command,
      });
    });

    it('includes the provider and service key when a server group transformer is missing', () => {
      expect(() =>
        runtime.services.serverGroupTransformer.convertServerGroupCommandToDeployConfiguration({
          selectedProvider: provider,
        }),
      ).toThrowError(`No "serverGroup.transformer" service found for provider "${provider}"`);
    });
  });
});
