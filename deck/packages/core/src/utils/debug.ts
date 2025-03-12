export const now = performance ? performance.now.bind(performance) : Date.now.bind(Date);
// tslint:disable-next-line:no-console
const log = console.log.bind(console); // eslint-disable-line
import { padStart } from 'lodash';

const getMethodName = (target: any, propertyKey: string) => {
  if (typeof target === 'object' && target.constructor && target.constructor.name) {
    return `${target.constructor.name}.${propertyKey}():`;
  }
  return `${propertyKey}():`;
};

/**
 * A method decorator which logs how much time was spent in each invocation of the method
 *
 * class Foo {
 *   @DebugTiming("Foo.slowMethod:")
 *   slowMethod() {
 *     ...
 *   }
 * }
 */
export const DebugTiming = (label?: string) => (target: any, propertyKey: string, descriptor: PropertyDescriptor) => {
  const fn = descriptor.value;
  label = padStart(label ? label : getMethodName(target, propertyKey), 50);
  descriptor.value = function () {
    const start = now();
    const result = fn.apply(this, arguments);
    const delta = now() - start;
    log(`${label} ${delta} ms`);
    return result;
  };
};

/**
 * A method decorator which periodically logs how much cumulative time has been spent in the method
 *
 * class Foo {
 *   @DebugTimingCumulative("Foo.slowMethod():", 1000)
 *   slowMethod() {
 *     ...
 *   }
 * }
 */
export const DebugTimingCumulative = (label?: string, logInterval = 5000) => (
  target: any,
  propertyKey: string,
  descriptor: PropertyDescriptor,
) => {
  const fn = descriptor.value;
  let count = 0;
  label = padStart(label ? label : getMethodName(target, propertyKey), 50);
  let cumulativeTime = 0;
  setInterval(() => log(`${label} ${padStart('' + count, 10)} calls in ${cumulativeTime} ms`), logInterval);
  descriptor.value = function () {
    count++;
    const start = now();
    const result = fn.apply(this, arguments);
    cumulativeTime += now() - start;
    return result;
  };
};
