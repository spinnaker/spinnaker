import { createDiagnosticLogger } from './diagnosticLogger';

describe('createDiagnosticLogger', () => {
  it('keeps errors and warnings observable without emitting lower-severity diagnostics', () => {
    const error = jasmine.createSpy('error');
    const warn = jasmine.createSpy('warn');
    const logger = createDiagnosticLogger({ error, warn });

    logger.error('request failed', 500);
    logger.warn('request delayed', 1000);
    logger.debug('debug');
    logger.info('info');
    logger.log('log');

    expect(error).toHaveBeenCalledOnceWith('request failed', 500);
    expect(warn).toHaveBeenCalledOnceWith('request delayed', 1000);
  });
});
