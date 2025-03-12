import { setConsoleOptions } from '@storybook/addon-console';

setConsoleOptions({
  panelInclude: [/{/],
});

export const parameters = {
  options: {
    storySort: {
      order: ['Forms', ['Intro']],
    },
  },
};
