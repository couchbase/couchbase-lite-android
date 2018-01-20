package com.couchbase.lite;


import com.couchbase.lite.utils.IOUtils;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Locale;

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

        String uri = String.format(Locale.ENGLISH, "blips://%s:4995/beer", this.config.remoteHost());
        ReplicatorConfiguration.Builder builder = makeConfig(false, true, false, uri);
        run(builder.build(), kC4NetErrTLSCertUntrusted, "Network");
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

        String uri = String.format(Locale.ENGLISH, "blips://%s:%d/beer",
                this.config.remoteHost(), this.config.remotePort());
        ReplicatorConfiguration.Builder builder = makeConfig(false, true, false, uri);
        builder.setPinnedServerCertificate(cert);
        run(builder.build(), 0, null);
    }
}
