const LEVELS = {
  DEBUG: 1,
  INFO: 2,
  WARN: 3,
  ERROR: 4,
};

const getLevel = (level?: Level) => {
  return LEVELS[level || 'INFO'];
};

type Level = keyof typeof LEVELS;

export interface LoggerEvent {
  level?: Level;
  action: string;
  category?: string;
  error?: Error;
  data?: Record<string, any>;
}

export interface LoggerSubscriber {
  key: string;
  level?: Level;
  onEvent: (event: LoggerEvent) => void;
}

class Logger {
  private loggers: LoggerSubscriber[] = [];

  // This allows us to ignore noisy ghost errors - more details: https://stackoverflow.com/a/50387233/5737533
  public ignoredErrors = [
    'ResizeObserver loop limit exceeded',
    'ResizeObserver loop completed with undelivered notifications.',
  ];

  subscribe(newLogger: LoggerSubscriber) {
    this.loggers.push(newLogger);
    return () => this.loggers.filter((logger) => logger !== newLogger);
  }

  unsubscribe(key: string) {
    this.loggers.filter((logger) => logger.key !== key);
  }

  log(event: LoggerEvent) {
    if (event.error?.message && this.ignoredErrors.includes(event.error.message)) return;
    this.loggers.forEach((logger) => {
      if (getLevel(event.level) >= getLevel(logger.level)) {
        logger.onEvent(event);
      }
    });
  }
}

export const logger = new Logger();
