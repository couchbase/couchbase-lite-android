/**
 * Created by Pasin Suriyentrakorn on 4/10/15.
 * <p/>
 * Copyright (c) 2015 Couchbase, Inc All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 */

package com.couchbase.lite.support;

import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.support.action.Action;
import com.couchbase.lite.support.action.ActionBlock;
import com.couchbase.lite.support.action.ActionException;

import junit.framework.Assert;

import java.util.concurrent.atomic.AtomicBoolean;

public class ActionTest extends LiteTestCase {
    public void testSuccess() throws Exception {
        final StringBuilder str = new StringBuilder("Test");
        final AtomicBoolean didCleanUp = new AtomicBoolean(false);
        Action seq = new Action();
        seq.add(
            new ActionBlock() {
                @Override
                public void execute() throws ActionException {
                    str.insert(1, "his is a t");
                }
            },
            new ActionBlock() {
                @Override
                public void execute() throws ActionException {
                    str.delete(1, 11);
                }
            }, new ActionBlock() {
                @Override
                public void execute() throws ActionException {
                    didCleanUp.set(true);
                }
            }
        );

        seq.run();
        Assert.assertNull(seq.getLastError());
        Assert.assertEquals("This is a test", str.toString());
        Assert.assertTrue(didCleanUp.get());
    }

    public void testFailure() throws Exception {
        final StringBuilder str = new StringBuilder("Test");
        Action seq = new Action();
        final AtomicBoolean didCleanUp = new AtomicBoolean(false);
        seq.add(
            new ActionBlock() {
                @Override
                public void execute() throws ActionException {
                    str.insert(1, "his is a t");
                }
            },
            new ActionBlock() {
                @Override
                public void execute() throws ActionException {
                    str.delete(1, 11);
                }
            }, new ActionBlock() {
                @Override
                public void execute() throws ActionException {
                    didCleanUp.set(true);
                }
            }
        );

        seq.add(
            new ActionBlock() {
                @Override
                public void execute() throws ActionException {
                    throw new ActionException("Error");
                }
            },
            new ActionBlock() {
                @Override
                public void execute() throws ActionException {
                    Assert.fail("Shouldn't backout this step");
                }
            }, new ActionBlock() {
                @Override
                public void execute() throws ActionException {
                    Assert.fail("Shouldn't clean up this step");
                }
            }
        );

        ActionException error = null;
        try {
            seq.run();
        } catch (ActionException e) {
            error = e;
        }
        Assert.assertNotNull(error);
        Assert.assertEquals(seq.getLastError(), error);
        Assert.assertEquals(seq.getFailedStep(), 1);
        Assert.assertEquals("Test", str.toString());
        Assert.assertFalse(didCleanUp.get());
    }
}
