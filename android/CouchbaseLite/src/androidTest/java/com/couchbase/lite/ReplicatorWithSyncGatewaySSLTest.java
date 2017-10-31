package com.couchbase.lite;


import com.couchbase.lite.utils.IOUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import static com.couchbase.litecore.C4Constants.NetworkError.kC4NetErrTLSCertUntrusted;

/**
 * Note: https://github.com/couchbase/couchbase-lite-core/tree/master/Replicator/tests/data
 */
public class ReplicatorWithSyncGatewaySSLTest extends BaseReplicatorTest {
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * This test assumes an SG is serving SSL at port 4994 with a self-signed cert.
     */
    @Test
    public void testSelfSignedSSLFailure() throws InterruptedException {
        if (!config.replicatorTestsEnabled()) return;

        String uri = String.format(Locale.ENGLISH, "blips://%s:4995/beer", this.config.remoteHost());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        run(config, kC4NetErrTLSCertUntrusted, "Network");
    }

    /**
     * This test assumes an SG is serving SSL at port 4994 with a self-signed cert equal to the one
     * stored in the test resource SelfSigned.cer. (This is the same cert used in the 1.x unit tests.)
     */
    @Test
    public void testSelfSignedSSLPinned() throws InterruptedException, IOException {
        if (!config.replicatorTestsEnabled()) return;

        timeout = 180; // seconds

        InputStream is = getAsset("cert.cer");
        byte[] cert = IOUtils.toByteArray(is);
        is.close();

        String uri = String.format(Locale.ENGLISH, "blips://%s:%d/beer",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration config = makeConfig(false, true, false, uri);
        config.setPinnedServerCertificate(cert);
        run(config, 0, null);
    }
}
