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
package org.reaktivity.nukleus.http2.internal.types.stream;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.reaktivity.nukleus.http2.internal.types.HttpHeaderFW;
import org.reaktivity.nukleus.http2.internal.types.ListFW;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.reaktivity.nukleus.http2.internal.types.stream.Http2Flags.END_HEADERS;
import static org.reaktivity.nukleus.http2.internal.types.stream.Http2FrameType.PUSH_PROMISE;

/*

    Flyweight for HTTP2 HEADERS frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============+===============================================+
    |Pad Length? (8)|
    +-+-------------+-----------------------------------------------+
    |R|                  Promised Stream ID (31)                    |
    +-+-----------------------------+-------------------------------+
    |                   Header Block Fragment (*)                 ...
    +---------------------------------------------------------------+
    |                           Padding (*)                       ...
    +---------------------------------------------------------------+

 */
public class Http2PushPromiseFW extends Http2FrameFW
{

    private static final int FLAGS_OFFSET = 4;
    private static final int PAYLOAD_OFFSET = 9;

    private final HpackHeaderBlockFW headerBlockRO = new HpackHeaderBlockFW();

    @Override
    public Http2FrameType type()
    {
        return PUSH_PROMISE;
    }

    public boolean padded()
    {
        return Http2Flags.padded(flags());
    }

    public boolean endHeaders()
    {
        return Http2Flags.endHeaders(flags());
    }

    public int promisedStreamId()
    {
        int offset = offset() + PAYLOAD_OFFSET + (padded() ? 1 : 0);
        return buffer().getInt(offset, BIG_ENDIAN) & 0x7F_FF_FF_FF;
    }

    private int headersOffset()
    {
        return offset() + PAYLOAD_OFFSET + 4 + (padded() ? 1 : 0);
    }

    private int headersLength()
    {
        int headersLength = payloadLength() - 4;    // -4 for promised stream id
        if (padded())
        {
            int padding = buffer().getByte(offset() + PAYLOAD_OFFSET) & 0xff;
            headersLength = headersLength - padding - 1;    // -1 for Pad Length field
        }

        return headersLength;
    }

    public void forEach(Consumer<HpackHeaderFieldFW> headerField)
    {
        headerBlockRO.forEach(headerField);
    }

    @Override
    public Http2PushPromiseFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);
        int streamId = streamId();
        if (streamId == 0)
        {
            throw new IllegalArgumentException(
                    String.format("Invalid PUSH_PROMISE frame stream-id=%d (must not be 0)", streamId));
        }
        headerBlockRO.wrap(buffer(), headersOffset(), headersOffset() + headersLength());

        checkLimit(limit(), maxLimit);
        return this;
    }

    @Override
    public String toString()
    {
        return String.format("%s frame <length=%s, type=%s, flags=%s, id=%s>",
                type(), payloadLength(), type(), flags(), streamId());
    }

    public static final class Builder extends Http2FrameFW.Builder<Builder, Http2PushPromiseFW>
    {
        private final HpackHeaderBlockFW.Builder blockRW = new HpackHeaderBlockFW.Builder();

        public Builder()
        {
            super(new Http2PushPromiseFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            blockRW.wrap(buffer, offset + PAYLOAD_OFFSET + 4, maxLimit());

            return this;
        }

        public Builder endHeaders()
        {
            byte flags = buffer().getByte(offset() + FLAGS_OFFSET);
            flags |= END_HEADERS;
            buffer().putByte(offset() + FLAGS_OFFSET, flags);
            return this;
        }

        public Builder promisedStreamId(int streamId)
        {
            buffer().putInt(offset() + PAYLOAD_OFFSET, streamId, BIG_ENDIAN);
            return this;
        }

        public Builder header(Consumer<HpackHeaderFieldFW.Builder> mutator)
        {
            blockRW.header(mutator);
            int length = blockRW.limit() - offset() - PAYLOAD_OFFSET;
            payloadLength(length);
            return this;
        }

        public Builder set(
                ListFW<HttpHeaderFW> listRO,
                BiFunction<HttpHeaderFW, HpackHeaderFieldFW.Builder, HpackHeaderFieldFW> mapper)
        {
            blockRW.set(listRO, mapper);
            int length = blockRW.limit() - offset() - PAYLOAD_OFFSET;
            payloadLength(length);
            return this;
        }

    }
}

