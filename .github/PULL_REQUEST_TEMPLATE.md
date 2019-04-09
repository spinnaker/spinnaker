We prefer small, well tested pull requests.

Please refer to [Contributing to Spinnaker](https://spinnaker.io/community/contributing/).

When filling out a pull request, please consider the following:

* Follow the commit message conventions [found here](http://www.spinnaker.io/v1.0/docs/how-to-submit-a-patch).
* Provide a descriptive summary for your changes.
* If it fixes a bug or resolves a feature request, be sure to link to that issue.
* Add inline code comments to changes that might not be obvious.
* Squash your commits as you keep adding changes.
* Add a comment to @spinnaker/reviewers for review if your issue has been outstanding for more than 3 days.

Note that we are unlikely to accept pull requests that add features without prior discussion. The best way to propose a feature is to open an issue first and discuss your ideas there before implementing them.


----------
# Helpful Tips

## Add screenshots and annotations
<kbd>![](https://cl.ly/744faf8f96d7/download/Image%202019-04-08%20at%2012.35.32.png)</kbd>
- In OSX Mojave, you can capture screenshots and add annotations. https://support.apple.com/en-us/HT201361
- Another app you can use is https://www.getcloudapp.com/.

## Add a gif to show an action
GIFs are great to show an action, however they sometimes move too fast. 
One way to improve them is to capture 3 pieces of content:
- screenshot before an action
- screenshot after the action
- gif of the action
- You can edit a gif using https://ezgif.com/

## Put a border around a screenshot/gif
Add a border to help improve the readability.
```html
<kbd>![](https://www.spinnaker.io/assets/images/spinnaker-logo-inline.svg)</kbd>
```

## Add expandable sections
<details>
<summary>Add expandable sections to hide away large code blocks/images or reduce the motion of a gifs</summary>

```js
const example = 'spinnaker spinnaker SPINNAKER';
```

<kbd>![](https://www.spinnaker.io/assets/images/spinnaker-logo-inline.svg)</kbd>

<kbd>![](https://cl.ly/90d191c48dab/download/spinnaker-loader.gif)</kbd>
</details>
