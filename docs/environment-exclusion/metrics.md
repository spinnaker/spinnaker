# Metrics (environment exclusion)

### lease.env.count

A counter which tracks the number of times that clients attempt to take a lease.

Tags:

* action: `verification` or `actuation`
* outcome:
  - `granted` if lease was granted
  - `denied` if lease was denied
* status:
  - `free` if there was no existing lease
  - `expired` if there was an existing lease, but it was expired
  - `active` if there was an existing lease that is still active (success will be `false`)

### lease.env.duration

A percentile timer which records the amount of time spent holding a lease or attempting to get a lease.

Note that this includes the time spent initiating an actuation or a verification since those actions are performed while holding the lease.

Tags:
* action: `verification` or `actuation`
* outcome:
  - `granted` if lease was granted
  - `denied` if lease was denied

