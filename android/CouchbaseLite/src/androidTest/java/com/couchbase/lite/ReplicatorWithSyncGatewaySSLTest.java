package com.couchbase.lite;


import com.couchbase.lite.utils.IOUtils;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import static com.couchbase.litecore.C4Constants.NetworkError.kC4NetErrTLSCertUntrusted;

/**
 * Note: https://github.com/couchbase/couchbase-lite-core/tree/master/Replicator/tests/data
 */
public class ReplicatorWithSyncGatewaySSLTest extends BaseReplicatorTest {
    /**
     * This test assumes an SG is serving SSL at port 4994 with a self-signed cert.
     */
    @Test
    public void testSelfSignedSSLFailure() throws InterruptedException, URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        Endpoint target = getRemoteEndpoint("beer", true);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        run(config, kC4NetErrTLSCertUntrusted, "Network");
    }

    /**
     * This test assumes an SG is serving SSL at port 4994 with a self-signed cert equal to the one
     * stored in the test resource SelfSigned.cer. (This is the same cert used in the 1.x unit tests.)
     */
    @Test
    public void testSelfSignedSSLPinned() throws InterruptedException, IOException, URISyntaxException {
        if (!config.replicatorTestsEnabled()) return;

        timeout = 180; // seconds

        InputStream is = getAsset("cert.cer");
        byte[] cert = IOUtils.toByteArray(is);
        is.close();

        Endpoint target = getRemoteEndpoint("beer", true);
        ReplicatorConfiguration config = makeConfig(false, true, false, target);
        config.setPinnedServerCertificate(cert);
        run(config, 0, null);
    }
}
