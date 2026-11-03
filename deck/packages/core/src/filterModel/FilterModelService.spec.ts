import { setDirectRouter } from '../navigation/directRouter';
import { FilterModelService } from './FilterModelService';
import type { IFilterConfig, IFilterModel } from './IFilterModel';

describe('FilterModelService direct router integration', () => {
  afterEach(() => {
    setDirectRouter(null);
  });

  it('registers shared filter hooks and hydrates permalink params', () => {
    const onBefore = vi.fn();
    const onStart = vi.fn();
    const onSuccess = vi.fn();
    const stateGlob = '**.application.insight.test.**';
    const router = { transitionService: { onBefore, onStart, onSuccess } };
    const config: IFilterConfig[] = [
      { model: 'account', param: 'acct', type: 'trueKeyObject' },
      { model: 'filter', param: 'q', type: 'string' },
    ];
    const filterModel = FilterModelService.configureFilterModel({} as IFilterModel, config);
    setDirectRouter(router as any);

    FilterModelService.registerRouterHooks(filterModel, stateGlob);

    expect(onSuccess).toHaveBeenCalledWith({ exiting: stateGlob, retained: '**.application' }, expect.any(Function));
    expect(onBefore).toHaveBeenCalledWith({ entering: stateGlob, retained: '**.application' }, expect.any(Function));
    expect(onStart).toHaveBeenCalledWith({ exiting: '**.application' }, expect.any(Function));
    const hydrateHook = onBefore.mock.calls.find(([criteria]) => criteria.to === stateGlob)?.[1];

    expect(hydrateHook).toEqual(expect.any(Function));
    hydrateHook({ params: () => ({ acct: { production: true }, q: 'payments' }) });
    expect(filterModel.sortFilter).toEqual({ account: { production: true }, filter: 'payments' });
  });
});
