## One Icon Font to Rule Them All

The goal is to migrate Spinnaker's design system Iconography to one font file that only contains icons used in the project. This will help on our road to supporting mobile, simplify usage, and allow any future designer to maintain without knowing git.

## Icomoon

[Icomoon.io](https://icomoon.io/) provides a GUI for uploading, managing, and exporting icon fonts.

To update the icon font using Icomoon, do the following:

- grab the JSON in our repo: `deck/app/fonts/spinnaker/selection.json`
- go to [Icomoon.io](https://icomoon.io/) and create a new project (the title doesn't matter)
- click the "Import Icons" button and select `selection.json` and import all settings
- now select "Import Icons" again to choose your addition(s) (svg format is best)
- when satisfied with the changes, click "Generate Font" at the bottom.
- make sure all glyphs are selected, then click "Download" at the bottom.
- After unzipping the download, rename `styles.css` to `icons.css`
- replace the following files in the deck repo with your new ones:
  `icomoon.svg`, `icomoon.ttf`, `icomoon.woff`, `selection.json`, `icons.css`
- you may need to run `npx prettier --write app/fonts/**/*.{css,json}` if prettier has not picked up the new files.

## Icon Resources

The Spinnaker project currently uses icons from multiple libraries, but will be migrating to [Streamline Light](http://streamlineicons.com). This package was decided based on its volume of technical icons, and its visual pairing with Source Sans, the Spinnaker font.

**Netflix Teammates** can find the Netflix-licensed Streamline icons in:
`Google Drive⁩ ▸ ⁨Team Drives⁩ ▸ ⁨Delivery Engineering⁩ ▸ ⁨Delivery Experience (DEx)⁩ ▸ Iconography`

All custom icons are there as well.
