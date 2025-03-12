package com.netflix.spinnaker.keel.persistence

/**
 * A lease that can be checked out from the lease repository
 *
 * To release the lease, call [close] on it
 */
interface Lease : AutoCloseable

