import {
  applyApplicationInitializers,
  registerApplicationInitializer,
  resetApplicationInitializersForTests,
} from './application.initializers';

describe('application.initializers', () => {
  beforeEach(() => resetApplicationInitializersForTests());

  it('applies registered initializers once for the current context', () => {
    const applicationState = {} as any;
    const uiRouter = {} as any;
    const initializer = jasmine.createSpy('initializer');

    registerApplicationInitializer(initializer);
    applyApplicationInitializers(applicationState, uiRouter);
    applyApplicationInitializers(applicationState, uiRouter);

    expect(initializer).toHaveBeenCalledTimes(1);
    expect(initializer).toHaveBeenCalledWith(applicationState, uiRouter);
  });

  it('runs late registrations immediately after the context has been applied', () => {
    const applicationState = {} as any;
    const uiRouter = {} as any;
    const initializer = jasmine.createSpy('initializer');

    applyApplicationInitializers(applicationState, uiRouter);
    registerApplicationInitializer(initializer);

    expect(initializer).toHaveBeenCalledTimes(1);
    expect(initializer).toHaveBeenCalledWith(applicationState, uiRouter);
  });
});
