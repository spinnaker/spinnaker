const path = require('path');

module.exports = {
  plugins: {
    'postcss-import': {},
    'postcss-extend': {},
    'autoprefixer': {},
    'postcss-style-guide': {
      project: 'Spinnaker',
      dest: path.join(__dirname, '..', '..', 'public', 'styleguide.html'),
      showCode: false,
      themePath: path.join(__dirname, '..', '..', 'src', 'styleguide-template')
    },
    'cssnano': {}
  }
};
