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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * Defines an URL handler for handling "jarinjar:" protocol
 * @author Florent Benoit
 */
public class BootstrapURLHandler extends URLStreamHandler {

    /**
     * Repository of URLs.
     */
    private final EntriesRepository entriesRepository;

    /**
     * Constructor with predefined repository.
     * @param entriesRepository the repository
     */
    public BootstrapURLHandler(EntriesRepository entriesRepository) {
        this.entriesRepository = entriesRepository;
    }

    /**
     * We build a connection to the specified jarinjar URL.
     * @param url the URL to connect to
     * @return the jarinjar URL connection.
     */
    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new JarInJarURLConnection(url, entriesRepository);
    }

}
