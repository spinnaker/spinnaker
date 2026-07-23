import type { ILogService } from 'angular';

export interface DiagnosticSink {
  error: (...args: unknown[]) => void;
  warn: (...args: unknown[]) => void;
}

const noop = (): void => undefined;

export function createDiagnosticLogger(sink: DiagnosticSink = console): ILogService {
  return ({
    debug: noop,
    error: (...args: unknown[]) => sink.error(...args),
    info: noop,
    log: noop,
    warn: (...args: unknown[]) => sink.warn(...args),
  } as unknown) as ILogService;
}

export const diagnosticLogger = createDiagnosticLogger();
