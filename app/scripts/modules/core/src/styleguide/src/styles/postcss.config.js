const path = require('path');

module.exports = {
  plugins: {
    'postcss-import': {},
    'postcss-extend': {},
    'autoprefixer': {},
    'postcss-scopeit': {scopeName: 'styleguide'},
    'postcss-style-guide': {
      project: 'Spinnaker',
      dest: path.join(__dirname, '..', '..', 'src', 'public', 'styleguide.html'),
      showCode: false,
      themePath: path.join(__dirname, '..', '..', 'src', 'styleguide-template')
    },
    'cssnano': {}
  }
}
