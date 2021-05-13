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

interface Event {
  level?: Level;
  message: string;
  error?: Error;
  data?: object;
}

export interface LoggerSubscriber {
  key: string;
  level?: Level;
  onEvent: (event: Event) => void;
}

class Logger {
  private loggers: LoggerSubscriber[] = [];

  subscribe(newLogger: LoggerSubscriber) {
    this.loggers.push(newLogger);
    return () => this.loggers.filter((logger) => logger !== newLogger);
  }

  unsubscribe(key: string) {
    this.loggers.filter((logger) => logger.key !== key);
  }

  log(event: Event) {
    this.loggers.forEach((logger) => {
      if (getLevel(event.level) >= getLevel(logger.level)) {
        logger.onEvent(event);
      }
    });
  }
}

export const logger = new Logger();
