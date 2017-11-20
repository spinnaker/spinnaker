const colorMap = require('./src/styleguide/src/colorMap');

module.exports = {
    plugins: {
        'autoprefixer': {},
        'postcss-colorfix': {
          colors: colorMap
        }
    }
};
