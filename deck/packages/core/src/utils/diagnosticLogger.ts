export type DiagnosticSink = Pick<Console, 'error' | 'warn'>;
export type DiagnosticLogger = Pick<Console, 'debug' | 'error' | 'info' | 'log' | 'warn'>;

const noop = (..._args: unknown[]): void => undefined;

export function createDiagnosticLogger(sink: DiagnosticSink = console): DiagnosticLogger {
  return {
    debug: noop,
    error: (...args: unknown[]) => sink.error(...args),
    info: noop,
    log: noop,
    warn: (...args: unknown[]) => sink.warn(...args),
  };
}

export const diagnosticLogger = createDiagnosticLogger();
