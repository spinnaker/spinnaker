package com.netflix.spinnaker.keel.core

// Validating a comment field on [Pinned/Vetoed] payloads
fun validateComment(comment: String?) {
  if (comment != null) {
    require(comment.length <= 255) {
      "Comments should have a maximum length of 255 characters."
    }
  }
}
