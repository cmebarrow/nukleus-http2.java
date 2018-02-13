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

package org.reaktivity.nukleus.http2.internal;

import org.agrona.DirectBuffer;
import org.reaktivity.nukleus.http2.internal.types.HttpHeaderFW;
import org.reaktivity.nukleus.http2.internal.types.ListFW;
import org.reaktivity.nukleus.http2.internal.types.stream.AckFW;
import org.reaktivity.nukleus.http2.internal.types.stream.Http2ErrorCode;
import org.reaktivity.nukleus.http2.internal.types.stream.RegionFW;
import org.reaktivity.nukleus.http2.internal.types.stream.TransferFW;

import java.io.Closeable;

/*
 * Writes HTTP2 frames to a connection. There are multiple streams multiplexed in
 * a connection, there are different ways to send data on the connection
 * (when there is a window). For e.g some implementations are:
 *
 * 1. HTTP2 streams are selected randomly
 * 2. HTTP2 streams are selected based on the priority of streams
 * 3. HTTP2 streams are selected in a round-robin way
 ...
 */
public interface WriteScheduler extends Closeable
{

    boolean windowUpdate(int streamId, int update);

    boolean pingAck(DirectBuffer buffer, int offset, int length);

    boolean goaway(int lastStreamId, Http2ErrorCode errorCode);

    boolean rst(int streamId, Http2ErrorCode errorCode);

    boolean settings(int maxConcurrentStreams);

    boolean settingsAck();

    boolean headers(int streamId, byte flags, ListFW<HttpHeaderFW> headers);

    boolean pushPromise(int streamId, int promisedStreamId, ListFW<HttpHeaderFW> headers);

    boolean data(int streamId, TransferFW data);

    boolean dataEos(int streamId);

    void doEnd();

    void onAck(AckFW ack);

    void onHttp2Window();

    void onHttp2Window(int streamId);

    @Override
    void close();

    interface Entry
    {
    }

}
