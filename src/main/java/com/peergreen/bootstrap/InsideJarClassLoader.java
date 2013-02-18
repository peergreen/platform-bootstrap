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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureClassLoader;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * JarInJar classloader allowing to load resources and classes from embedding jar in a root jar.
 * @author Florent Benoit
 */
public class InsideJarClassLoader extends SecureClassLoader {

    /**
     * Repository containing the byte entries.
     */
    private final EntriesRepository entriesRepository;

    /**
     * AccessControlContext used to define the classes.
     */
    private final AccessControlContext accessControlContext;

    /**
     * Build a new classloader with the given parent classloader and the given repository.
     * @param parent the parent classloader
     * @param entriesRepository the repository used to get the classes/resources
     */
    public InsideJarClassLoader(ClassLoader parent, EntriesRepository entriesRepository) {
        super(parent);
        this.entriesRepository = entriesRepository;
        this.accessControlContext = AccessController.getContext();
    }


    /**
     * Try to find the given class specified by its name.
     * @return the defined class if found.
     */
    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        try {
            return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<Class<?>>() {
                        @Override
                        public Class<?> run() throws ClassNotFoundException {
                            ByteEntry byteEntry = getRepository().getByteEntry(name);
                            if (byteEntry == null) {
                                throw new ClassNotFoundException("Unable to find class '" +  name + "'.");
                            }

                            // Define Package if possible
                            definePackage(name, byteEntry.getCodesource().getLocation());

                            // Define the class
                            Class<?> clazz = defineClass(name, byteEntry.getBytes(), 0, byteEntry.getBytes().length, byteEntry.getCodesource());

                            // Remove associated bytecode no longer needed
                            getRepository().removeClassEntry(name);

                            // return the class.
                            return clazz;
                        }
                    }, accessControlContext);
        } catch (PrivilegedActionException e) {
           throw new ClassNotFoundException("Unable to find the class with name '" + name + "'.", e);
        }

    }

    /**
     * Define the Package of the given class if not already defined.
     * @param className Fully qualified name of the class (binary name)
     * @param location Url of the jar containing the class (and the manifest)
     */
    private void definePackage(String className, URL location) {

        // Construct package name
        String packageName = (className.contains(".")) ? className.substring(0, className.lastIndexOf(".")) : "";

        // Build Package only if not already loaded
        Package p = getPackage(packageName);
        if (p == null) {

            String url = location.toString();
            String jarName = url.substring(url.lastIndexOf("!") + 2);
            try {

                // Gather the manifest from the repository
                URL manifestUrl = getRepository().getURL(jarName, "META-INF/MANIFEST.MF");
                try(InputStream is = manifestUrl.openStream()) {

                    // LOad the manifest and build/define a Package from its values
                    Manifest manifest = new Manifest(is);
                    Attributes main = manifest.getMainAttributes();
                    boolean sealed = "true".equalsIgnoreCase(main.getValue(Attributes.Name.SEALED));
                    definePackage(packageName,
                            main.getValue(Attributes.Name.SPECIFICATION_TITLE),
                            main.getValue(Attributes.Name.SPECIFICATION_VERSION),
                            main.getValue(Attributes.Name.SPECIFICATION_VENDOR),
                            main.getValue(Attributes.Name.IMPLEMENTATION_TITLE),
                            main.getValue(Attributes.Name.IMPLEMENTATION_VERSION),
                            main.getValue(Attributes.Name.IMPLEMENTATION_VENDOR),
                            (sealed) ? location : null
                            );
                } catch (IOException e) {
                    // If there is any error, fallback to the default Package creation
                    definePackage(packageName, null, null, null, null, null, null, null);
                }
            } catch (MalformedURLException e) {
                // If there is any error, fallback to the default Package creation
                definePackage(packageName, null, null, null, null, null, null, null);
            }
        }
    }

    /**
     * Try to find an URL providing the specified resource name.
     * @param name to resource to search
     */
    @Override
    protected URL findResource(String name) {
        return entriesRepository.getURL(name);

    }

    /**
     * Try to find all matching URLs for the given resource name.
     * @param name the name of the resource
     */
    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        return entriesRepository.getURLs(name);
    }


    protected EntriesRepository getRepository() {
        return entriesRepository;
    }

    /**
     * Allows to extends dynamically the classloading.
     * @param url the URL to add.
     */
    protected void addURL(URL url) {

    }

}
