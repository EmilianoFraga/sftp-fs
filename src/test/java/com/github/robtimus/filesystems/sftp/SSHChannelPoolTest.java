/*
 * SSHChannelPoolTest.java
 * Copyright 2019 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.filesystems.sftp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import com.github.robtimus.filesystems.sftp.SSHChannelPool.Channel;

@SuppressWarnings({ "nls", "javadoc" })
public class SSHChannelPoolTest extends AbstractSFTPFileSystemTest {

    @Test(timeout = 1000L)
    public void testGetWithTimeout() throws Exception {
        final int clientCount = 3;

        URI uri = getURI();
        SFTPEnvironment env = createEnv()
                .withClientConnectionCount(clientCount)
                .withClientConnectionWaitTimeout(500, TimeUnit.MILLISECONDS);

        SSHChannelPool pool = new SSHChannelPool(uri.getHost(), uri.getPort(), env);
        List<Channel> channels = Collections.emptyList();
        try {
            // exhaust all available clients
            channels = claimChannels(pool, clientCount);

            long startTime = System.currentTimeMillis();
            try {
                claimChannel(pool);
                fail("Should never get here.");

            } catch (IOException e) {
                String expected = SFTPMessages.clientConnectionWaitTimeoutExpired();

                assertEquals("timeout expired exception thrown", expected, e.getMessage());
                assertTrue("timeout after specified duration", System.currentTimeMillis() - startTime >= 500);
            }
        } finally {
            pool.close();
            for (Channel channel : channels) {
                channel.close();
            }
        }
    }

    @SuppressWarnings("resource")
    private List<Channel> claimChannels(SSHChannelPool pool, int clientCount) throws IOException {
        List<Channel> channels = new ArrayList<>(clientCount);
        for (int i = 0; i < clientCount; i++) {
            channels.add(pool.get());
        }
        return channels;
    }

    @SuppressWarnings("resource")
    private void claimChannel(SSHChannelPool pool) throws IOException {
        pool.get();
    }
}
