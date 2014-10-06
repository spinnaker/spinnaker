package com.netflix.spinnaker.igor.history.model

/**
 * A history entry that contains a build detail
 */
class BuildDetails {

    BuildContent content
    Map details = [
        type  : 'build',
        source: 'igor'
    ]

}
