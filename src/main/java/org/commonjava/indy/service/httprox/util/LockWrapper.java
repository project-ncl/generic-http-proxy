/*
 * Copyright 2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.commonjava.indy.service.httprox.util;

import java.util.concurrent.locks.Lock;

import com.google.common.util.concurrent.Striped;

public class LockWrapper {

    private final static Striped<Lock> striped = Striped.lock(50);

    public static Lock getLockByKey(String key) {
        return striped.get(key);
    }

}
