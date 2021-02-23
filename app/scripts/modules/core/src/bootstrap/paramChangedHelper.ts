import { HookResult, TargetState, Transition } from '@uirouter/core';

type ParamChangedCallback = (newVal: any, oldVal?: any) => HookResult | TargetState;

export const paramChangedHelper = (paramName: string, changedHandler: ParamChangedCallback) => {
  return (transition: Transition): HookResult => {
    const previousValue = transition.params('from')[paramName];
    const newValue = transition.params('to')[paramName];

    if (previousValue === newValue) {
      return null;
    }
    return changedHandler(newValue, previousValue);
  };
};
