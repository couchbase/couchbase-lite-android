//
// ReplicatorWithSyncGatewaySSLTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite;

import android.support.test.InstrumentationRegistry;

import com.couchbase.lite.utils.Config;
import com.couchbase.lite.utils.IOUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import static com.couchbase.litecore.C4Constants.NetworkError.kC4NetErrTLSCertUntrusted;

/**
 * Note: https://github.com/couchbase/couchbase-lite-core/tree/master/Replicator/tests/data
 */
public class ReplicatorWithSyncGatewaySSLTest extends BaseReplicatorTest {
    @Before
    public void setUp() throws Exception {
        config = new Config(InstrumentationRegistry.getContext().getAssets().open(Config.TEST_PROPERTIES_FILE));
        if (!config.replicatorTestsEnabled())
            return;

        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        if (!config.replicatorTestsEnabled())
            return;

        super.tearDown();
    }

    /**
     * This test assumes an SG is serving SSL at port 4994 with a self-signed cert.
     */
    @Test
    public void testSelfSignedSSLFailure() throws InterruptedException, URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        Endpoint target = getRemoteEndpoint("beer", true);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        run(config, kC4NetErrTLSCertUntrusted, "NetworkDomain");
    }
    
    @Test
    public void testSelfSignedSSLPinned() throws InterruptedException, IOException, URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        timeout = 180; // seconds
        InputStream is = getAsset("cert.cer"); // this is self-signed certificate. Can not pass with Android platform.
        byte[] cert = IOUtils.toByteArray(is);
        is.close();

        Endpoint target = getRemoteEndpoint("beer", true);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        config.setPinnedServerCertificate(cert);
        run(config, 0, null);
    }
}
