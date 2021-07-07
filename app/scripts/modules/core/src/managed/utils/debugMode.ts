const debugFeatureFlag = {
  key: 'MD_debug',
  value: '1',
};

export const getIsDebugMode = () => {
  return localStorage.getItem(debugFeatureFlag.key) === debugFeatureFlag.value;
};

export const setDebugMode = (isDebug: boolean) => {
  if (isDebug) {
    localStorage.setItem(debugFeatureFlag.key, debugFeatureFlag.value);
  } else {
    localStorage.removeItem(debugFeatureFlag.key);
  }
};
