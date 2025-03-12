/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker.model

import static java.util.Comparator.comparing
import static java.util.Comparator.naturalOrder
import static java.util.Comparator.nullsFirst

import groovy.transform.ToString

/**
 * Represents a Wercker Run
 */
@ToString
class Run {
    String id
    String url
    String branch
    String commitHash
    Date createdAt
    Date finishedAt
    Date startedAt

    String message
    int progress
    String result
    String status

    Owner user
    String pipelineId
    Pipeline pipeline
    Application application

    static final public Comparator<Run> startedAtComparator =
        comparing({r -> r.startedAt?: r.createdAt}, nullsFirst(naturalOrder()))

    static final public Comparator<Run> finishedAtComparator =
        comparing({r -> r.finishedAt}, nullsFirst(naturalOrder()))
}
