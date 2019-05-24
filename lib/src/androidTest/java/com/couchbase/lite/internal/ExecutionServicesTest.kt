//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal


import com.couchbase.lite.LogDomain
import com.couchbase.lite.internal.support.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Stack
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit


// This currently tests the AndroidExecutionService, on a device.
// It should test the current implementatin of ExecutionService, on a VM
class ExecutionServicesTest {
    private val executionService = AndroidExecutionService()

    // The main executor always uses the same thread.
    @Test
    fun testMainThreadExecutor() {
        val latch = CountDownLatch(2)

        val threads = arrayOfNulls<Thread>(2)

        executionService.mainExecutor.execute {
            threads[0] = Thread.currentThread()
            latch.countDown()
        }

        executionService.mainExecutor.execute {
            threads[1] = Thread.currentThread()
            latch.countDown()
        }

        try { latch.await(2, TimeUnit.SECONDS) }
        catch (ignore: InterruptedException) { }

        assertEquals(threads[0], threads[1])
    }

    // The serial executor executes in order.
    @Test
    fun testSerialExecutor() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val stack = Stack<String>()

        val executor = executionService.serialExecutor

        executor.execute {
            try {
                startLatch.await() // wait for the 2nd task to be scheduled.
                Thread.sleep(1000)
            }
            catch (ignore: InterruptedException) { }

            synchronized(stack) { stack.push("ONE") }

            finishLatch.countDown()
        }

        executor.execute {
            synchronized(stack) { stack.push("TWO") }
            finishLatch.countDown()
        }

        // allow the first task to proceed.
        startLatch.countDown()

        try {finishLatch.await(5, TimeUnit.SECONDS) }
        catch (ignore: InterruptedException) {  }

        synchronized(stack) {
            assertEquals("TWO", stack.pop())
            assertEquals("ONE", stack.pop())
        }
    }

    // A stopped executor throws on further attempts to schedule
    @Test(expected = RejectedExecutionException::class)
    fun testStoppedSerialExecutorRejects() {
        val executor = executionService.serialExecutor
        assertTrue(executor.stop(0, TimeUnit.SECONDS)) // no tasks
        executor.execute { Log.d(LogDomain.ALL, "This test is about to fail!") }
    }

    // A stopped executor can finish currently queued tasks.
    @Test
    fun testStoppedSerialExecutorCompletes() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val executor = executionService.serialExecutor

        executor.execute {
            try { startLatch.await() }
            catch (ignore: InterruptedException) { }

            finishLatch.countDown()
        }

        executor.execute {
            try { startLatch.await() }
            catch (ignore: InterruptedException) { }

            finishLatch.countDown()
        }

        assertFalse(executor.stop(0, TimeUnit.SECONDS))

        try {
            executor.execute { Log.d(LogDomain.ALL, "This test is about to fail!") }
            fail("Stopped executor should not accept new tasks")
        }
        catch (expected: RejectedExecutionException) { }

        // allow the tasks to proceed.
        startLatch.countDown()

        try { assertTrue(finishLatch.await(5, TimeUnit.SECONDS)) }
        catch (ignore: InterruptedException) { }

        assertTrue(executor.stop(5, TimeUnit.SECONDS)) // everything should be done shortly
    }

    // The concurrent executor can executes out of order.
    @Test
    fun testConcurrentExecutor() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val stack = Stack<String>()

        val executor = executionService.concurrentExecutor

        executor.execute {
            try {
                startLatch.await() // wait for the 2nd task to be scheduled.
                Thread.sleep(1000)
            }
            catch (ignore: InterruptedException) { }

            synchronized(stack) { stack.push("ONE") }

            finishLatch.countDown()
        }

        executor.execute {
            synchronized(stack) { stack.push("TWO") }
            finishLatch.countDown()
        }

        // allow the first task to proceed.
        startLatch.countDown()

        try { finishLatch.await(5, TimeUnit.SECONDS) }
        catch (ignore: InterruptedException) { }

        // tasks should finish in reverse start order
        synchronized(stack) {
            assertEquals("ONE", stack.pop())
            assertEquals("TWO", stack.pop())
        }
    }

    // A stopped Executor throws on further attempts to schedule
    @Test(expected = RejectedExecutionException::class)
    fun testStoppedConcurrentExecutorRejects() {
        val executor = executionService.concurrentExecutor
        assertTrue(executor.stop(0, TimeUnit.SECONDS)) // no tasks
        executor.execute { Log.d(LogDomain.ALL, "This test is about to fail!") }
    }

    // A stopped Executor finishes currently queued tasks.
    @Test
    fun testStoppedConcurrentExecutorCompletes() {
        val startLatch = CountDownLatch(1)
        val finishLatch = CountDownLatch(2)

        val executor = executionService.concurrentExecutor

        // enqueue two tasks
        executor.execute {
            try { startLatch.await() }
            catch (ignore: InterruptedException) { }

            finishLatch.countDown()
        }

        executor.execute {
            try { startLatch.await() }
            catch (ignore: InterruptedException) { }

            finishLatch.countDown()
        }

        assertFalse(executor.stop(0, TimeUnit.SECONDS))

        try {
            executor.execute { Log.d(LogDomain.ALL, "This test is about to fail!") }
            fail("Stopped executor should not accept new tasks")
        }
        catch (expected: RejectedExecutionException) { }

        // allow the tasks to proceed.
        startLatch.countDown()

        try { assertTrue(finishLatch.await(5, TimeUnit.SECONDS)) }
        catch (ignore: InterruptedException) { }

        assertTrue(executor.stop(5, TimeUnit.SECONDS)) // everything should be done shortly
    }

    // The scheduler schedules on the passed queue, with the proper delay.
    @Test
    fun testEnqueueWithDelay() {
        val finishLatch = CountDownLatch(1)

        val threads = arrayOfNulls<Thread>(2)
        val executionTime = LongArray(1)

        val executor = executionService.mainExecutor

        // get the thread used by the executor
        // note that only the mainThreadExecutor guarantees execution on a single thread...
        executor.execute { threads[0] = Thread.currentThread() }

        val queuedTime = System.currentTimeMillis()

        executionService.postDelayedOnExecutor(
                777,
                executor,
                Runnable {
                    executionTime[0] = System.currentTimeMillis()
                    threads[1] = Thread.currentThread()
                    finishLatch.countDown()
                })

        try { assertTrue(finishLatch.await(5, TimeUnit.SECONDS)) }
        catch (ignore: InterruptedException) { }

        assertTrue(Math.abs(queuedTime + 777 - executionTime[0]) < 10)
        assertEquals(threads[0], threads[1])
    }

    // A delayed task can be cancelled
    @Test
    fun testCancelDelayedTask() {
        val completed = BooleanArray(1)

        // schedule far enough in the future so that there is plenty of time to cancel it
        // but not so far that we have to wait a long tim to be sure it didn't run.
        val task = executionService.postDelayedOnExecutor(
                100,
                executionService.concurrentExecutor,
                Runnable { completed[0] = true })

        executionService.cancelDelayedTask(task)

        try { Thread.sleep(200) }
        catch (ignore: InterruptedException) { }

        assertFalse(completed[0])
    }
}
