import { ITitusServiceJobProcesses } from '../../../domain/ITitusServiceJobProcesses';

export const processesList = ['disableIncreaseDesired', 'disableDecreaseDesired'];

export const enabledProcesses = (processesMap: ITitusServiceJobProcesses): string[] => {
  return Object.keys(processesMap).reduce((enabled, process) => {
    if (processesMap[process]) {
      enabled.push(process);
    }
    return enabled;
  }, []);
};
