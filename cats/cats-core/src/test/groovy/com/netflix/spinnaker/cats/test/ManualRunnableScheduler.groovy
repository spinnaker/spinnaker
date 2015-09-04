/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.cats.test

import java.util.concurrent.*

class ManualRunnableScheduler implements ScheduledExecutorService {
    private final Collection<Callable<?>> callables = new LinkedList<>()

    private static class RunnableWrapper implements Callable<Object> {
        private final Runnable runnable;

        RunnableWrapper(Runnable runnable) {
            this.runnable = runnable
        }

        @Override
        Object call() throws Exception {
            runnable.run()
            return null
        }
    }

    private static class ScheduledFutureImpl implements ScheduledFuture {
        private final Callable callable;

        ScheduledFutureImpl(Callable callable) {
            this.callable = callable
        }

        @Override
        long getDelay(TimeUnit unit) {
            return 0
        }

        @Override
        int compareTo(Delayed o) {
            return 0
        }

        @Override
        boolean cancel(boolean mayInterruptIfRunning) {
            return false
        }

        @Override
        boolean isCancelled() {
            return false
        }

        @Override
        boolean isDone() {
            return true
        }

        @Override
        Object get() throws InterruptedException, ExecutionException {
            try {
                return callable.call()
            } catch (Throwable e) {
                throw new ExecutionException(e)
            }
        }

        @Override
        Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            get()
        }
    }

    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        schedule(new RunnableWrapper(command), delay, unit)
    }

    @Override
    def <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        callables.add(callable);
        new ScheduledFutureImpl(callable)
    }

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        schedule(command, period, unit)
    }

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        schedule(command, delay, unit)
    }

    @Override
    List<Runnable> shutdownNow() {
        return []
    }

    @Override
    boolean isShutdown() {
        return false
    }

    @Override
    boolean isTerminated() {
        return false
    }

    @Override
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return true
    }

    @Override
    def <T> Future<T> submit(Callable<T> task) {
        callables.add(task)
        new ScheduledFutureImpl(task)
    }

    @Override
    def <T> Future<T> submit(Runnable task, T result) {
        submit(new RunnableWrapper(task))
    }

    @Override
    Future<?> submit(Runnable task) {
        submit(task, null)
    }

    @Override
    def <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        tasks.collect { submit(it) }
    }

    @Override
    def <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        invokeAll(tasks)
    }

    @Override
    def <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        tasks.first().call()
    }

    @Override
    def <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        invokeAny(tasks)
    }

    @Override
    void execute(Runnable command) {
        callables.add(new RunnableWrapper(command))
    }

    @Override
    void shutdown() {
        callables.clear()
    }

    public void runAll() {
        for (Callable callable : callables) {
            callable.call()
        }
    }
}
