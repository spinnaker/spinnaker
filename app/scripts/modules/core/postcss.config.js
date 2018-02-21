const colorMap = require('@spinnaker/styleguide/src/colorMap');

module.exports = {
    plugins: {
        'autoprefixer': {},
        'postcss-colorfix': {
          colors: colorMap
        }
    }
};
