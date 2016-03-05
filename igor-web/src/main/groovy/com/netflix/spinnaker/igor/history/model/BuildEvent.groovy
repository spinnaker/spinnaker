package com.netflix.spinnaker.igor.history.model

/**
 * A history entry that contains a build detail
 */
class BuildEvent extends Event {

    BuildContent content
    Map details = [
        type  : 'build',
        source: 'igor'
    ]

}
