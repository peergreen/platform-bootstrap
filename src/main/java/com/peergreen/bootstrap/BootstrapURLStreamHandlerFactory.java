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

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * URLStreamHandler Factory that allows to handle the "jarinjar" protocol.
 * @author Florent Benoit
 */
public class BootstrapURLStreamHandlerFactory implements URLStreamHandlerFactory {

    /**
     * Repository of URLs.
     */
    private final EntriesRepository entriesRepository;

    /**
     * Jar in Jar protocol.
     */
    public static final String JAR_IN_JAR_PROTOCOL = "jarinjar";

    /**
     * Constructor with predefined repository.
     * @param entriesRepository the repository
     */
    public BootstrapURLStreamHandlerFactory(EntriesRepository entriesRepository) {
        this.entriesRepository = entriesRepository;
    }

    /**
     * Create handler for handling jarinjar URLs.
     * @param protocol the protocol to check
     * @return the handler that can handle our jarinjar URLs
     */
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (JAR_IN_JAR_PROTOCOL.equals(protocol)) {
            return new BootstrapURLHandler(entriesRepository);
        }

        // We only manage jarinjar URLs so return null for other protocols
        return null;
    }

}
