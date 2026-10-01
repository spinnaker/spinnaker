import { createDiagnosticLogger } from './diagnosticLogger';

describe('createDiagnosticLogger', () => {
  it('keeps errors and warnings observable without emitting lower-severity diagnostics', () => {
    const error = vi.fn();
    const warn = vi.fn();
    const logger = createDiagnosticLogger({ error, warn });

    logger.error('request failed', 500);
    logger.warn('request delayed', 1000);
    logger.debug('debug');
    logger.info('info');
    logger.log('log');

    expect(error).toHaveBeenCalledExactlyOnceWith('request failed', 500);
    expect(warn).toHaveBeenCalledExactlyOnceWith('request delayed', 1000);
  });
});
