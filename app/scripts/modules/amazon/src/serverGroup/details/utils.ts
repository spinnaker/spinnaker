// Returns the base image name from the image description.
export const getBaseImageName = (description?: string) => {
  let name: string;
  const tags = (description || '').split(', ');
  tags.forEach((tag: string) => {
    const keyVal = tag.split('=');
    if (keyVal.length === 2 && keyVal[0] === 'ancestor_name') {
      name = keyVal[1];
    }
  });

  return name;
};
