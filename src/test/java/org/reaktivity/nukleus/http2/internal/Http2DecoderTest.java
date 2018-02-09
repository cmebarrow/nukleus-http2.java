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
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.reaktivity.nukleus.buffer.MemoryManager;
import org.reaktivity.nukleus.http2.internal.types.ListFW;
import org.reaktivity.nukleus.http2.internal.types.stream.Http2DataFW;
import org.reaktivity.nukleus.http2.internal.types.stream.Http2FrameFW;
import org.reaktivity.nukleus.http2.internal.types.stream.Http2HeadersFW;
import org.reaktivity.nukleus.http2.internal.types.stream.Http2SettingsFW;
import org.reaktivity.nukleus.http2.internal.types.stream.RegionFW;
import org.reaktivity.reaktor.internal.buffer.DefaultDirectBufferBuilder;
import org.reaktivity.reaktor.internal.layouts.MemoryLayout;
import org.reaktivity.reaktor.internal.memory.DefaultMemoryManager;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.reaktivity.nukleus.http2.internal.types.stream.HpackLiteralHeaderFieldFW.LiteralType.INCREMENTAL_INDEXING;
import static org.reaktivity.nukleus.http2.internal.types.stream.Http2FrameType.DATA;
import static org.reaktivity.nukleus.http2.internal.types.stream.Http2FrameType.HEADERS;
import static org.reaktivity.nukleus.http2.internal.types.stream.Http2FrameType.SETTINGS;
import static org.reaktivity.nukleus.http2.internal.types.stream.Http2PrefaceFW.PRI_REQUEST;

public class Http2DecoderTest
{

    private int frameCount = 0;

    @Test
    public void decodeSimple()
    {
        MemoryManager memoryManager = memoryManager();
        MutableDirectBuffer buf = new UnsafeBuffer(new byte[0]);
        long addressOffset = memoryManager.acquire(32768);
        long resolvedOffset = memoryManager.resolve(addressOffset);
        buf.wrap(resolvedOffset, 32768);

        // PRI
        int offset = 10;            // non-zero offset
        buf.putBytes(offset, PRI_REQUEST);
        Region r1 = new Region(addressOffset + offset, PRI_REQUEST.length, 3);

        // HEADERS
        offset += PRI_REQUEST.length + 10;
        Http2HeadersFW headers = writeHeaders(buf, offset);
        Region r2 = new Region(addressOffset + offset, headers.sizeof(), 3);

        // DATA
        offset = headers.limit() + 10;
        Http2DataFW data = writeData(buf, offset);
        Region r3 = new Region(addressOffset + offset, data.sizeof(), 3);

        // SETTINGS
        offset = data.limit() + 10;
        Http2SettingsFW settings = writeSettings(buf, offset);
        Region r4 = new Region(addressOffset + offset, settings.sizeof(), 3);

        Http2Decoder decoder = new Http2Decoder(memoryManager, DefaultDirectBufferBuilder::new,
                Settings.DEFAULT_MAX_FRAME_SIZE, this::frame, null, null);

        MutableDirectBuffer regionBuf = new UnsafeBuffer(new byte[4096]);
        ListFW<RegionFW> regionRO = new ListFW.Builder<>(new RegionFW.Builder(), new RegionFW())
                .wrap(regionBuf, 0, regionBuf.capacity())
                .item(m -> m.address(r1.address).length(r1.length).streamId(r1.streamId))
                .item(m -> m.address(r2.address).length(r2.length).streamId(r2.streamId))
                .item(m -> m.address(r3.address).length(r3.length).streamId(r3.streamId))
                .item(m -> m.address(r4.address).length(r4.length).streamId(r4.streamId))
                .build();
        decoder.decode(regionRO);

        assertEquals(3, frameCount);
    }

    @Test
    public void decode()
    {
        MemoryManager memoryManager = memoryManager();
        MutableDirectBuffer buf = new UnsafeBuffer(new byte[0]);
        long addressOffset = memoryManager.acquire(32768);
        long resolvedOffset = memoryManager.resolve(addressOffset);
        buf.wrap(resolvedOffset, 32768);

        // PRI
        int offset = 0;
        buf.putBytes(offset, PRI_REQUEST);
        offset += PRI_REQUEST.length;

        // HEADERS
        Http2HeadersFW headers = writeHeaders(buf, offset);
        offset = headers.limit();

        // DATA
        Http2DataFW data = writeData(buf, offset);
        offset = data.limit();

        // SETTINGS
        Http2SettingsFW settings = writeSettings(buf, offset);
        offset = settings.limit();

        // Test with all region length combinations
        for(int regionLength=0; regionLength < offset; regionLength++)
        {
            frameCount = 0;

            List<Integer> addressList = IntStream.range(0, offset).boxed().collect(Collectors.toList());
            List<Region> regions = batches(addressList, regionLength + 1)
                    .stream()
                    .map(l -> new Region(addressOffset + l.get(0), l.size(), 3))
                    .collect(Collectors.toList());

            Http2Decoder decoder = new Http2Decoder(memoryManager, DefaultDirectBufferBuilder::new,
                    Settings.DEFAULT_MAX_FRAME_SIZE, this::frame, null, null);

            List<List<Region>> regionBatches = batches(regions, 2);
            for(List<Region> regionBatch : regionBatches)
            {
                MutableDirectBuffer regionBuf = new UnsafeBuffer(new byte[4096]);
                ListFW.Builder<RegionFW.Builder, RegionFW> list =
                        new ListFW.Builder<>(new RegionFW.Builder(), new RegionFW())
                                .wrap(regionBuf, 0, regionBuf.capacity());
                list.iterate(regionBatch, r -> list.item(b -> b.address(r.address).length(r.length).streamId(r.streamId)));
                ListFW<RegionFW> regionRO = list.build();
                decoder.decode(regionRO);
            }

            assertEquals(3, frameCount);
        }
    }

    private Http2DataFW writeData(MutableDirectBuffer buf, int offset)
    {
        byte[] bytes = "123456789012345678901234567890".getBytes();
        DirectBuffer payload = new UnsafeBuffer(bytes);
        return new Http2DataFW.Builder()
                .wrap(buf, offset, buf.capacity())
                .streamId(5)
                .payload(payload)
                .build();
    }

    private Http2HeadersFW writeHeaders(MutableDirectBuffer buf, int offset)
    {
        return new Http2HeadersFW.Builder()
                .wrap(buf, offset, buf.capacity())   // non-zero offset
                .header(h -> h.indexed(2))      // :method: GET
                .header(h -> h.indexed(6))      // :scheme: http
                .header(h -> h.indexed(4))      // :path: /
                .header(h -> h.literal(l -> l.type(INCREMENTAL_INDEXING).name(1).value("www.example.com")))
                .endHeaders()
                .streamId(3)
                .build();
    }

    private Http2SettingsFW writeSettings(MutableDirectBuffer buf, int offset)
    {
        return new Http2SettingsFW.Builder()
                    .wrap(buf, offset, buf.capacity())
                    .ack()
                    .build();
    }

    private void frame(Http2FrameFW frame)
    {
        switch (frameCount)
        {
            case 0:
                assertEquals(HEADERS, frame.type());
                break;
            case 1:
                assertEquals(DATA, frame.type());
                break;
            case 2:
                assertEquals(SETTINGS, frame.type());
                break;
            default:
                throw new IllegalStateException("Illegal frame count = " + frameCount);
        }
        frameCount++;
    }

    private MemoryManager memoryManager()
    {
        Path outputFile = new File("target/nukleus-itests/memory0").toPath();
        MemoryLayout.Builder mlb = new MemoryLayout.Builder()
                .path(outputFile);

        MemoryLayout layout = mlb.minimumBlockSize(1024)
                                 .maximumBlockSize(65536)
                                 .create(true)
                                 .build();
        return new DefaultMemoryManager(layout);
    }

    private static <T> List<List<T>> batches(List<T> source, int length) {
        int size = source.size();
        int fullChunks = (size - 1) / length;
        return IntStream.range(0, fullChunks + 1)
                        .mapToObj(n -> source.subList(n * length, n == fullChunks ? size : (n + 1) * length))
                        .collect(Collectors.toList());
    }

    private static final class Region
    {
        final long address;
        final int length;
        final long streamId;

        Region(long address, int length, long streamId)
        {
            this.address = address;
            this.length = length;
            this.streamId = streamId;
        }

        public String toString()
        {
            return String.format("[address=%d length=%d]", address, length);
        }
    }
}
