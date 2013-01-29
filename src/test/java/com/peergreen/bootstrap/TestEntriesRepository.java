/**
 * Copyright 2013 Peergreen S.A.S.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.peergreen.bootstrap;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Enumeration;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestEntriesRepository {


    @Test
    public void testCheckEntries() throws Exception {

        // Search test.jar
        URL testJarURL = TestEntriesRepository.class.getResource("/test.jar");
        Assert.assertNotNull(testJarURL);

        EntriesRepository repository = new EntriesRepository(testJarURL);

        // Register our URL factory
        Field f = URL.class.getDeclaredField("factory");
        f.setAccessible(true);
        f.set(null, null);
        URL.setURLStreamHandlerFactory(new BootstrapURLStreamHandlerFactory(repository));

        // scan
        repository.scan();

        // now test the results
        ByteEntry byteEntry = repository.getByteEntry("com.peergreen.test.MyClass");
        Assert.assertNotNull(byteEntry);

        // We should get the first entry stored in a.jar and not in b.jar
        Assert.assertEquals(byteEntry.getBytes().length, 262);

        URL firstA = repository.getURL("a.txt");
        ByteEntry byteEntryA = repository.getByteEntry(firstA);
        Assert.assertNotNull(byteEntryA);
        Assert.assertEquals(byteEntryA.getBytes(), "FirstA\n".getBytes(Charset.defaultCharset()));

        URL firstB = repository.getURL("b.txt");
        ByteEntry byteEntryB = repository.getByteEntry(firstB);
        Assert.assertNotNull(byteEntryB);
        Assert.assertEquals(byteEntryB.getBytes(), "FirstB\n".getBytes(Charset.defaultCharset()));

        URL firstC = repository.getURL("sub/entry/c.txt");
        ByteEntry byteEntryC = repository.getByteEntry(firstC);
        Assert.assertNotNull(byteEntryC);
        Assert.assertEquals(byteEntryC.getBytes(), "FirstC\n".getBytes(Charset.defaultCharset()));


        // Now check for entries in b.jar too
        // we should get first url from a.jar and second from b.jar
        Enumeration<URL> aEntries = repository.getURLs("a.txt");
        Assert.assertTrue(aEntries.hasMoreElements());
        URL urlaA = aEntries.nextElement();
        Assert.assertTrue(urlaA.getPath().contains("a.jar"));
        ByteEntry byteEntryUrlaA = repository.getByteEntry(urlaA);
        Assert.assertNotNull(byteEntryUrlaA);
        Assert.assertEquals(byteEntryUrlaA.getBytes(), "FirstA\n".getBytes(Charset.defaultCharset()));


        //
        Assert.assertTrue(aEntries.hasMoreElements());
        URL urlaB = aEntries.nextElement();
        Assert.assertTrue(urlaB.getPath().contains("b.jar"));
        ByteEntry byteEntryUrlaB = repository.getByteEntry(urlaB);
        Assert.assertNotNull(byteEntryUrlaB);
        Assert.assertEquals(byteEntryUrlaB.getBytes(), "SecondA\n".getBytes(Charset.defaultCharset()));


    }
}
