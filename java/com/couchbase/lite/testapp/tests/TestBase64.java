package com.couchbase.lite.testapp.tests;


import android.test.InstrumentationTestCase;

import junit.framework.Assert;

import java.io.IOException;

public class TestBase64 extends InstrumentationTestCase {


    public void testDecode() throws IOException {

        String input1 = "eyJwdWJsaWMta2V5Ijp7ImFsZ29yaXRobSI6IkRTIiwieSI6ImNhNWJiYTYzZmI4MDQ2OGE0MjFjZjgxYTIzN2VlMDcwYTJlOTM4NTY0ODhiYTYzNTM0ZTU4NzJjZjllMGUwMDk0ZWQ2NDBlOGNhYmEwMjNkYjc5ODU3YjkxMzBlZGNmZGZiNmJiNTUwMWNjNTk3MTI1Y2NiMWQ1ZWQzOTVjZTMyNThlYjEwN2FjZTM1ODRiOWIwN2I4MWU5MDQ4NzhhYzBhMjFlOWZkYmRjYzNhNzNjOTg3MDAwYjk4YWUwMmZmMDQ4ODFiZDNiOTBmNzllYzVlNDU1YzliZjM3NzFkYjEzMTcxYjNkMTA2ZjM1ZDQyZmZmZjQ2ZWZiZDcwNjgyNWQiLCJwIjoiZmY2MDA0ODNkYjZhYmZjNWI0NWVhYjc4NTk0YjM1MzNkNTUwZDlmMWJmMmE5OTJhN2E4ZGFhNmRjMzRmODA0NWFkNGU2ZTBjNDI5ZDMzNGVlZWFhZWZkN2UyM2Q0ODEwYmUwMGU0Y2MxNDkyY2JhMzI1YmE4MWZmMmQ1YTViMzA1YThkMTdlYjNiZjRhMDZhMzQ5ZDM5MmUwMGQzMjk3NDRhNTE3OTM4MDM0NGU4MmExOGM0NzkzMzQzOGY4OTFlMjJhZWVmODEyZDY5YzhmNzVlMzI2Y2I3MGVhMDAwYzNmNzc2ZGZkYmQ2MDQ2MzhjMmVmNzE3ZmMyNmQwMmUxNyIsInEiOiJlMjFlMDRmOTExZDFlZDc5OTEwMDhlY2FhYjNiZjc3NTk4NDMwOWMzIiwiZyI6ImM1MmE0YTBmZjNiN2U2MWZkZjE4NjdjZTg0MTM4MzY5YTYxNTRmNGFmYTkyOTY2ZTNjODI3ZTI1Y2ZhNmNmNTA4YjkwZTVkZTQxOWUxMzM3ZTA3YTJlOWUyYTNjZDVkZWE3MDRkMTc1ZjhlYmY2YWYzOTdkNjllMTEwYjk2YWZiMTdjN2EwMzI1OTMyOWU0ODI5YjBkMDNiYmM3ODk2YjE1YjRhZGU1M2UxMzA4NThjYzM0ZDk2MjY5YWE4OTA0MWY0MDkxMzZjNzI0MmEzODg5NWM5ZDViY2NhZDRmMzg5YWYxZDdhNGJkMTM5OGJkMDcyZGZmYTg5NjIzMzM5N2EifSwicHJpbmNpcGFsIjp7ImVtYWlsIjoiamVuc0Btb29zZXlhcmQuY29tIn0sImlhdCI6MTM1ODI5NjIzNzU3NywiZXhwIjoxMzU4MzgyNjM3NTc3LCJpc3MiOiJsb2dpbi5wZXJzb25hLm9yZyJ9";
        String expected1 = "{\"public-key\":{\"algorithm\":\"DS\",\"y\":\"ca5bba63fb80468a421cf81a237ee070a2e93856488ba63534e5872cf9e0e0094ed640e8caba023db79857b9130edcfdfb6bb5501cc597125ccb1d5ed395ce3258eb107ace3584b9b07b81e904878ac0a21e9fdbdcc3a73c987000b98ae02ff04881bd3b90f79ec5e455c9bf3771db13171b3d106f35d42ffff46efbd706825d\",\"p\":\"ff600483db6abfc5b45eab78594b3533d550d9f1bf2a992a7a8daa6dc34f8045ad4e6e0c429d334eeeaaefd7e23d4810be00e4cc1492cba325ba81ff2d5a5b305a8d17eb3bf4a06a349d392e00d329744a5179380344e82a18c47933438f891e22aeef812d69c8f75e326cb70ea000c3f776dfdbd604638c2ef717fc26d02e17\",\"q\":\"e21e04f911d1ed7991008ecaab3bf775984309c3\",\"g\":\"c52a4a0ff3b7e61fdf1867ce84138369a6154f4afa92966e3c827e25cfa6cf508b90e5de419e1337e07a2e9e2a3cd5dea704d175f8ebf6af397d69e110b96afb17c7a03259329e4829b0d03bbc7896b15b4ade53e130858cc34d96269aa89041f409136c7242a38895c9d5bccad4f389af1d7a4bd1398bd072dffa896233397a\"},\"principal\":{\"email\":\"jens@mooseyard.com\"},\"iat\":1358296237577,\"exp\":1358382637577,\"iss\":\"login.persona.org\"}";


        String output1a = new String(android.util.Base64.decode(input1, android.util.Base64.DEFAULT));
        Assert.assertEquals(expected1, output1a);
        String output1b =  new String(com.couchbase.lite.util.Base64.decode(input1, com.couchbase.lite.util.Base64.DEFAULT));
        Assert.assertEquals(expected1, output1b);

        String input2 = "eyJleHAiOjEzNTgyOTY0Mzg0OTUsImF1ZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDk4NC8ifQ";
        String expected2 = "{\"exp\":1358296438495,\"aud\":\"http://localhost:4984/\"}";

        String output2a = new String(android.util.Base64.decode(input2, android.util.Base64.DEFAULT));
        Assert.assertEquals(expected2, output2a);
        String output2b = new String(com.couchbase.lite.util.Base64.decode(input2, com.couchbase.lite.util.Base64.DEFAULT));
        Assert.assertEquals(expected2, output2b);

    }

}
