/**
 * Copyright 2016-2017 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.nukleus.http2.internal.streams.server.rfc7540;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.reaktivity.reaktor.test.ReaktorRule;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

public class AbortIT
{
    private final K3poRule k3po = new K3poRule()
            .addScriptRoot("route", "org/reaktivity/specification/nukleus/http2/control/route")
            .addScriptRoot("spec", "org/reaktivity/specification/http2/rfc7540/connection.abort")
            .addScriptRoot("nukleus", "org/reaktivity/specification/nukleus/http2/streams/rfc7540/connection.abort");

    private final TestRule timeout = new DisableOnDebug(new Timeout(10, SECONDS));

    private final ReaktorRule reaktor = new ReaktorRule()
            .directory("target/nukleus-itests")
            .commandBufferCapacity(1024)
            .responseBufferCapacity(1024)
            .counterValuesBufferCapacity(1024)
            .nukleus("http2"::equals)
            .clean();

    @Rule
    public final TestRule chain = outerRule(reaktor).around(k3po).around(timeout);

    @Ignore("read aborted and write aborted race")
    @Test
    @Specification({
            "${route}/server/controller",
            "${spec}/client.sent.write.abort.on.open.request.response.buffered/client",
            "${nukleus}/client.sent.write.abort.on.open.request.response.buffered/server" })
    public void clientSentWriteAbortOnOpenRequestResponseBuffered() throws Exception
    {
        k3po.finish();
    }

    @Test
    @Specification({
            "${route}/server/controller",
            "${spec}/client.sent.read.abort.on.open.request.response.buffered/client",
            "${nukleus}/client.sent.read.abort.on.open.request.response.buffered/server" })
    public void clientSentReadAbortOnOpenRequestResponseBuffered() throws Exception
    {
        k3po.finish();
    }

    @Ignore("read aborted and write aborted race")
    @Test
    @Specification({
            "${route}/server/controller",
            "${spec}/server.sent.write.abort.on.open.request.response.buffered/client",
            "${nukleus}/server.sent.write.abort.on.open.request.response.buffered/server" })
    public void serverSentWriteAbortOnOpenRequestResponseBuffered() throws Exception
    {
        k3po.finish();
    }

    @Ignore("race between BEGIN and RESET")
    @Test
    @Specification({
            "${route}/server/controller",
            "${spec}/server.sent.read.abort.on.open.request.response.buffered/client",
            "${nukleus}/server.sent.read.abort.on.open.request.response.buffered/server" })
    public void serverSentReadAbortOnOpenRequestResponseBuffered() throws Exception
    {
        k3po.finish();
    }

}
