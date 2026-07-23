import * as ngimport from 'ngimport';

import { setDirectRouter } from '../navigation/directRouter';
import { FilterModelService } from './FilterModelService';
import type { IFilterConfig, IFilterModel } from './IFilterModel';

describe('FilterModelService direct router integration', () => {
  let originalInjector: typeof ngimport.$injector;

  beforeEach(() => {
    originalInjector = ngimport.$injector;
    (ngimport as any).$injector = undefined;
  });

  afterEach(() => {
    setDirectRouter(null);
    (ngimport as any).$injector = originalInjector;
  });

  it('registers shared filter hooks and hydrates permalink params without an Angular injector', () => {
    const onBefore = jasmine.createSpy('onBefore');
    const onStart = jasmine.createSpy('onStart');
    const onSuccess = jasmine.createSpy('onSuccess');
    const stateGlob = '**.application.insight.test.**';
    const router = { transitionService: { onBefore, onStart, onSuccess } };
    const config: IFilterConfig[] = [
      { model: 'account', param: 'acct', type: 'trueKeyObject' },
      { model: 'filter', param: 'q', type: 'string' },
    ];
    const filterModel = FilterModelService.configureFilterModel({} as IFilterModel, config);
    setDirectRouter(router as any);

    FilterModelService.registerRouterHooks(filterModel, stateGlob);

    expect(onSuccess).toHaveBeenCalledWith({ exiting: stateGlob, retained: '**.application' }, jasmine.any(Function));
    expect(onBefore).toHaveBeenCalledWith({ entering: stateGlob, retained: '**.application' }, jasmine.any(Function));
    expect(onStart).toHaveBeenCalledWith({ exiting: '**.application' }, jasmine.any(Function));
    const hydrateHook = onBefore.calls.allArgs().find(([criteria]) => criteria.to === stateGlob)?.[1];

    expect(hydrateHook).toEqual(jasmine.any(Function));
    hydrateHook({ params: () => ({ acct: { production: true }, q: 'payments' }) });
    expect(filterModel.sortFilter).toEqual({ account: { production: true }, filter: 'payments' });
  });
});
