//
// EncodingTest.java
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.couchbase.lite.internal.fleece.AllocSlice;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class EncodingTest extends BaseTest {

    // https://github.com/couchbase/couchbase-lite-android/issues/1453
    @Test
    public void testFLEncode() throws Exception {
        testRoundTrip(42L);
        testRoundTrip(Long.MIN_VALUE);
        testRoundTrip("Fleece");
        Map<String, Object> map = new HashMap<>();
        map.put("foo", "bar");
        testRoundTrip(map);
        testRoundTrip(Arrays.asList((Object) "foo", "bar"));
        testRoundTrip(true);
        testRoundTrip(3.14F);
        testRoundTrip(Math.PI);
    }

    @Test
    public void testFLEncodeUTF8() throws Exception {
        testRoundTrip("Goodbye cruel world"); // one byte utf-8 chars
        testRoundTrip("Goodbye cruel £ world"); // a two byte utf-8 chars
        testRoundTrip("Goodbye cruel ᘺ world"); // a three byte utf-8 char

        testRoundTrip("Hello \uD83D\uDE3A World"); // a four byte utf-8 char: 😺
        testRoundTrip("Goodbye cruel \uD83D world", ""); // cheshire cat: half missing.
        testRoundTrip("Goodbye cruel \uD83D\uC03A world", ""); // a bad cat
        testRoundTrip("Goodbye cruel \uD83D\uDE3A\uDE3A world", ""); // a cat and a half
    }

    // These tests are built on the following fleece encoding.  Start at the end.
    // 0000: 44                                [4: this is a string; 4 bytes long]
    // 0001:     f0 9f 98 BA 00: "😺"          [1-5: cat; 0: pad to align on even byte]
    // 0006: 80 03            : &"😺" (@0000)  [80: this is a pointer; 03 3 2-byte units ago]
    @Test
    public void testUTF8Slices() {
        // https://github.com/couchbase/couchbase-lite-android/issues/1742
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0xBA, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            "\uD83D\uDE3A");

        // same as above, but byte 3 of the character is not legal
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xF0, (byte) 0x9F, (byte) 0x41, (byte) 0xBA, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // two 2-byte characters
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xC2, (byte) 0xA3, (byte) 0xC2, (byte) 0xA5, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            "£¥");

        // two 2-byte characters, 2nd byte of 2nd char is not legal
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xC2, (byte) 0xA3, (byte) 0xC2, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // a three byte character and a one byte character
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xE1, (byte) 0x98, (byte) 0xBA, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            "ᘺA");

        // a three byte character and a continuation byte
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xE1, (byte) 0x98, (byte) 0xBA, (byte) 0xBA, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // a three byte character with an illegal 2nd byte
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0xE1, (byte) 0x98, (byte) 0x41, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // four single byte characters
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            "AAAA");

        // four single byte characters one is a continuation character
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0x41, (byte) 0x98, (byte) 0x41, (byte) 0x41, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);

        // four single byte characters, byte 4 is illegal anywhere
        testSlice(
            new byte[] {
                (byte) 0x44, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0xC0, (byte) 0x00, (byte) 0x80, (byte) 0x03},
            null);
    }

    private void testRoundTrip(Object item) throws Exception { testRoundTrip(item, item); }

    private void testRoundTrip(Object item, Object expected) throws Exception {
        final FLEncoder encoder = new FLEncoder();
        AllocSlice slice = null;
        try {
            assertTrue(encoder.writeValue(item));
            slice = encoder.finish2();
            assertNotNull(slice);
            FLValue flValue = FLValue.fromData(slice);
            assertNotNull(flValue);
            Object obj = FLValue.toObject(flValue);
            assertEquals(expected, obj);
        }
        finally {
            if (slice != null) slice.free();
            encoder.free();
        }
    }

    private void testSlice(byte[] utf8Slice, String expected) {
        FLValue flValue = FLValue.fromData(utf8Slice);
        Object obj = FLValue.toObject(flValue);
        assertEquals(expected, obj);
    }
}
