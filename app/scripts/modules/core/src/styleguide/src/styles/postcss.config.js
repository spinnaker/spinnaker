module.exports = {
  plugins: {
    'postcss-import': {},
    'postcss-extend': {},
    'autoprefixer': {},
    'postcss-scopeit': {scopeName: 'styleguide'},
    'postcss-style-guide': {
      project: 'Spinnaker',
      dest: './app/scripts/modules/core/src/styleguide/public/styleguide.html',
      showCode: false,
      themePath: './app/scripts/modules/core/src/styleguide/src/styleguide-template/'
    },
    'cssnano': {}
  }
}
