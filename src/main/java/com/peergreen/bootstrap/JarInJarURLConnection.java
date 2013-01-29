/**
 * Copyright 2012 Peergreen S.A.S.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * JarInJar URL connection.
 * It allows to get resources from jar located in jar.(Only one level is supported of jar in jar).
 * @author Florent Benoit
 */
public class JarInJarURLConnection extends URLConnection {

    /**
     * Input stream of this connection.
     */
    private InputStream inputStream;

    /**
     * Repository of URLs.
     */
    private final EntriesRepository entriesRepository;


    /**
     * URL is following this format : jarinjar://<thefirst.jar>!<sub.jar>!/<entry in the subjar>
     * @param url the URL to parse
     */
    protected JarInJarURLConnection(URL url, EntriesRepository entriesRepository) throws IOException {
        super(url);
        this.entriesRepository = entriesRepository;
    }

    /**
     * Connect to the JarFile by extracting the inputstream.
     * @throws IOException if jar file cannot be analyzed.
     */
    @Override
    public void connect() throws IOException  {
        if (!connected) {

            ByteEntry byteEntry = entriesRepository.getByteEntry(url);
            if (byteEntry == null) {
                throw new IOException("Unable to find entry " + url + "");
            }
            this.inputStream = new ByteArrayInputStream(byteEntry.getBytes());
        }
    }

    /**
     * If we've not yet analyze the jar file, parse it before returning the stream
     * @return inputstream of this URL.
     * @throws IOException if connecting is failing
     */
    @Override
    public InputStream getInputStream() throws IOException {
        if (!connected) {
            connect();
        }
        return inputStream;
    }


    /**
     * @return length of the content specified by this URL connection
     */
    @Override
    public long getContentLengthLong() {
        return entriesRepository.getByteEntry(url).getBytes().length;
    }
}
