export const getContentReference = (uri: string): string => {
  return uri.replace(/^ref?:\/\//, '');
};
