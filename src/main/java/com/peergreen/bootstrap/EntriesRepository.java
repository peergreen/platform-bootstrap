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
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Repository for all entries that are found in the jars in the root jar.
 * @author Florent Benoit
 */
public class EntriesRepository {

    /**
     * Default buffer size for reading content of the Jar.
     */
    private static final int BUFFER = 4096;

    /**
     * Lib folder.
     */
    private static final String LIB_FOLDER = "lib/";

    /**
     * .jar extension.
     */
    private static final String JAR_EXTENSION = ".jar";

    /**
     * Root URL.
     */
    private final URL rootURL;

    /**
     * Mapping between a resource name and the associated URLs referencing this entry.
     */
    private final Map<String, List<URL>> urlEntries;

    /**
     * Mapping between <the name of a class (for .class resource) and URL for other resources> and <the byte entry>.
     */
    private final Map<String, ByteEntry> byteEntries;

    /**
     * Build a new repository around the given root URL.
     * @param rootURL the root URL.
     */
    public EntriesRepository(final URL rootURL) {
        this.rootURL = rootURL;
        this.urlEntries = new HashMap<String, List<URL>>();
        this.byteEntries = new HashMap<String, ByteEntry>();
    }

    public void scan() throws BootstrapException {
        // It's a jar file so scan entries
        try (JarFile jarFile = new JarFile(rootURL.toURI().getPath())) {

            // We search jar in jars
            Enumeration<JarEntry> entries = jarFile.entries();

            // Scan each entry
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                // Ignore entries that are not in the lib folder
                if (!entry.getName().startsWith(LIB_FOLDER)) {
                    continue;
                }

                // Select only inner jars
                if (!entry.getName().endsWith(JAR_EXTENSION)) {
                    continue;
                }

                // Use Zip* and not Jar* else it skip META-INF/MANIFEST.MF entries
                try (InputStream is = jarFile.getInputStream(entry); ZipInputStream zipInputStream = new ZipInputStream(is)) {

                    // Get current entry in subjar
                    ZipEntry subZipEntry = zipInputStream.getNextEntry();

                    // loop on all entries
                    while (subZipEntry != null) {

                        // get Entry Name
                        String subName = subZipEntry.getName();

                        // .class already exists ?
                        String className = null;
                        if (subName.endsWith(".class")) {
                            className = subName.substring(0, subName.length() - 6).replace("/", ".");
                            if (byteEntries.containsKey(className)) {
                                subZipEntry = zipInputStream.getNextEntry();
                                continue;
                            }
                        }


                        URL url = getURL(entry.getName(), subName);

                        List<URL> currentList = urlEntries.get(subName);
                        if (currentList == null) {
                            currentList = new ArrayList<URL>();
                            urlEntries.put(subName, currentList);
                        }
                        currentList.add(url);

                        // Now, gets bytes of the subzip entry
                        try(ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
                            byte[] b = new byte[BUFFER];

                            int len;
                            while ((len = zipInputStream.read(b, 0, b.length)) != -1) {
                                baos.write(b, 0, len);
                            }
                            byte[] bytes = baos.toByteArray();

                            // Build URL of the subJar
                            URL jarURL = new URL("jar:" + rootURL.toExternalForm() + "!/" + entry.getName());

                            // Build associated codesource
                            CodeSource codesource = new CodeSource(jarURL, (CodeSigner[]) null);

                            // Create byteEntry
                            ByteEntry byteEntry = new ByteEntry(codesource, bytes);

                            // add class with the name of the class
                            if (subName.endsWith(".class")) {
                                byteEntries.put(className, byteEntry);
                            } else if (!subName.endsWith("/")){
                                // Add only content, not the directories
                                byteEntries.put(url.toExternalForm(), byteEntry);
                            }
                        }
                        subZipEntry = zipInputStream.getNextEntry();

                    }
                } catch (IOException e) {
                    throw new BootstrapException("Unable to scan the jar '" + entry.getName() + "'", e);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new BootstrapException("Unable to scan the jar '" + rootURL + "'", e);
        }
    }

    /**
     * Remove the given class entry.
     * @param classname the name of the class to remove bytecode
     */
    public void removeClassEntry(String classname) {
        byteEntries.remove(classname);
    }

    /**
     * Gets bytes from the given URL.
     * @param url the given URL
     * @return the associated byte entry
     */
    public ByteEntry getByteEntry(URL url) {
        return byteEntries.get(url.toExternalForm());
    }

    /**
     * Gets bytes from the given classname.
     * @param classname the given name of the class
     * @return the associated byte entry
     */
    public ByteEntry getByteEntry(String classname) {
        return byteEntries.get(classname);
    }

    /**
     * Gets the first matching URL for the given resource name.
     * @param name the given resource name to search
     * @return the URL
     */
    public URL getURL(String name) {
        List<URL> urls = urlEntries.get(name);
        if (urls == null || urls.size() == 0) {
            return null;
        }
        return urls.get(0);

    }

    /**
     * Gets all the matching URL for the given resource name.
     * @param name the given resource name to search
     * @return the URLs
     */
    public Enumeration<URL> getURLs(String name) {
        List<URL> urls = urlEntries.get(name);
        if (urls == null || urls.size() == 0) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(urls);

    }


    /**
     * Build URL from given subjar and entry name.
     * @param jar the name of the jar contained in the root jar
     * @param name the entry name of the subjar
     * @return a jarInJar URL
     * @throws MalformedURLException if we're not able to build an URL
     */
    protected URL getURL(String jar, String name) throws MalformedURLException {
        URL url =  new URL(BootstrapURLStreamHandlerFactory.JAR_IN_JAR_PROTOCOL.concat(":/").concat(rootURL.getPath()).concat("!").concat(jar).concat("!").concat(name));
        return url;
    }

}
