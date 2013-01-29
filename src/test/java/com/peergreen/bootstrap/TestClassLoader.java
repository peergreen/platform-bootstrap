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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Enumeration;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestClassLoader {

    /**
     * Default buffer size for reading content of the Jar.
     */
    private static final int BUFFER = 4096;

    @Test
    public void testClassLoader() throws Exception {

        // Search test.jar
        URL testJarURL = TestClassLoader.class.getResource("/test.jar");
        Assert.assertNotNull(testJarURL);

        EntriesRepository repository = new EntriesRepository(testJarURL);

        // Register our URL factory
        Field f = URL.class.getDeclaredField("factory");
        f.setAccessible(true);
        f.set(null, null);
        URL.setURLStreamHandlerFactory(new BootstrapURLStreamHandlerFactory(repository));

        // Create ClassLoader
        InsideJarClassLoader insideJarClassLoader = new InsideJarClassLoader(null, repository);

        // scan
        repository.scan();

        // now test the results
        Class<?> myClass = insideJarClassLoader.loadClass("com.peergreen.test.MyClass");
        Assert.assertNotNull(myClass);

        // new instance
        Object myClassInstance = myClass.newInstance();
        Assert.assertNotNull(myClassInstance);

        // Get method
        Method method = myClass.getMethod("test");
        Assert.assertNotNull(method);

        // invoke
        Object result = method.invoke(myClassInstance);
        Assert.assertNotNull(result);
        Assert.assertEquals(result, 2503);


        URL firstA = insideJarClassLoader.getResource("a.txt");
        Assert.assertNotNull(firstA);
        Assert.assertEquals(firstA.getProtocol(), "jarinjar");
        Assert.assertEquals(getBytes(firstA), "FirstA\n".getBytes(Charset.defaultCharset()));

        // Check content length
        URLConnection connection = firstA.openConnection();
        Assert.assertEquals(connection.getContentLength(), 7);



        Enumeration<URL> aList = insideJarClassLoader.getResources("sub/entry/c.txt");
        Assert.assertTrue(aList.hasMoreElements());
        URL cAjar = aList.nextElement();

        Assert.assertNotNull(cAjar);
        Assert.assertTrue(cAjar.getPath().contains("a.jar"));
        Assert.assertEquals(getBytes(cAjar), "FirstC\n".getBytes(Charset.defaultCharset()));

        URL cBjar = aList.nextElement();
        Assert.assertFalse(aList.hasMoreElements());
        Assert.assertNotNull(cBjar);
        Assert.assertTrue(cBjar.getPath().contains("b.jar"));
        Assert.assertEquals(getBytes(cBjar), "SecondC\n".getBytes(Charset.defaultCharset()));


    }

    public byte[] getBytes(URL url) throws IOException {
        URLConnection urlConnection = url.openConnection();
        Assert.assertNotNull(urlConnection);

        try(InputStream inputStream = urlConnection.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
            Assert.assertNotNull(inputStream);

            byte[] b = new byte[BUFFER];

            int len;
            while ((len = inputStream.read(b, 0, b.length)) != -1) {
                baos.write(b, 0, len);
            }
            return  baos.toByteArray();
        }
    }

}
