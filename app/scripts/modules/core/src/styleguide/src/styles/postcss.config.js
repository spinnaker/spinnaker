module.exports = {
  plugins: {
    'postcss-import': {},
    'postcss-extend': {},
    'autoprefixer': {},
    'postcss-scopeit': {scopeName: 'styleguide'},
    'postcss-style-guide': {
      project: 'Spinnaker',
      dest: './src/styleguide/public/styleguide.html',
      showCode: false,
      themePath: './src/styleguide/src/styleguide-template/'
    },
    'cssnano': {}
  }
}
