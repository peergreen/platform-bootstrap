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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * Bootstrap class used to load delegating class.
 * @author Florent Benoit
 */
public class Bootstrap {

    private static final String NAMESPACE = "com.peergreen.bootstrap:";

    /**
     * If this System property is set to {@literal true}, the Bootstrap will force JVM's shutdown
     * with {@link System#exit(int)}.
     * By default, {@literal exit()} is not called.
     */
    private static final String SYSTEM_EXIT_ENABLED_PROPERTY = "com.peergreen.bootstrap.system.exit";

    /**
     * Keep arguments of the bootstrap in order to send them to the delegating class.
     */
    private final String[] args;

    /**
     * No public constructor as it's only used by this class.
     * @param args the arguments of this bootstrap
     */
    protected Bootstrap(String[] args) {
        this.args = args;
    }


    /**
     * Starts the bootstrap by invoking the main method of the delegating class
     * @throws BootstrapException if there is a failure.
     */
    public void start() throws BootstrapException {

        // Gets the location of the jar containing this class
        URL url = getLocation();

        // Scan entries in our all-in-one jar.
        EntriesRepository entriesRepository = new EntriesRepository(url);

        // Register our URL factory
        URL.setURLStreamHandlerFactory(new BootstrapURLStreamHandlerFactory(entriesRepository));

        // Scan entries
        long t0 = System.currentTimeMillis();
        entriesRepository.scan();
        long tEnd = System.currentTimeMillis();
        addBootstrapProperty("scan.begin", t0);
        addBootstrapProperty("scan.end", tEnd);

        // Create classloader with embedded jars
        ClassLoader classLoader = getClassLoader(entriesRepository);

        //FIXME: Allows to specify class to load
        // Class to load
        String classname = "com.peergreen.kernel.launcher.Kernel";

        // Load delegating class
        Class<?> mainClass = null;
        try {
            mainClass = classLoader.loadClass(classname);
        } catch (ClassNotFoundException e) {
            throw new BootstrapException("Unable to load the class '" + classname + "'.", e);
        }

        // Gets main method
        Method mainMethod = null;
        try {
            mainMethod = mainClass.getMethod("main",String[].class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new BootstrapException("The delegating class '" + classname + "' to launch has no main(String[] args) method available.", e);
        }

        addBootstrapProperty("main.invoke", tEnd);
        // Call main
        try {
            mainMethod.invoke(null, (Object) args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new BootstrapException("Unable to call the main method of the delegating class '" + classname + "'.", e);
        }

    }

    /**
     * Gets the URL of our location.
     * @return location
     */
    protected URL getLocation() {
        // gets the URL of our bootstrap jar through our protection domain
        return Bootstrap.class.getProtectionDomain().getCodeSource().getLocation();
    }

    /**
     * Build a classloader for this bootstrap.
     * @return a classloader that can access classes located in jars inside jar.
     * @throws BootstrapException if classloader cannot be built
     */
    protected ClassLoader getClassLoader(final EntriesRepository entriesRepository) throws BootstrapException {

        // Create the classloader using current context classloader as our parent
        try {
            return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<ClassLoader>() {
                        @Override
                        public ClassLoader run() throws ClassNotFoundException {
                            return new InsideJarClassLoader(Thread.currentThread().getContextClassLoader(), entriesRepository);
                        }
                    });
        } catch (PrivilegedActionException e) {
           throw new BootstrapException("Unable to get the classloader", e);
        }

    }



    /**
     * Starts the bootstrap which delegates the call to another class.
     * @param args the arguments of this bootstrap launcher
     */
    public static void main(String[] args) throws Exception {
        boolean exception = false;
        addBootstrapProperty("begin", System.currentTimeMillis());
        try {
            Bootstrap bootstrap = new Bootstrap(args);
            bootstrap.start();
        } catch (BootstrapException e) {
            e.printStackTrace(System.err);
            exception = true;
        } finally {
            terminate(exception);
        }
    }

    private static void terminate(boolean exception) {
        clearBootstrapProperties("begin", "scan.begin", "scan.end", "main.invoke");
        if (Boolean.getBoolean(SYSTEM_EXIT_ENABLED_PROPERTY)) {
            System.exit(exception ? -1 : 0);
        }
    }

    private static void clearBootstrapProperties(String... keys) {
        for (String key : keys) {
            System.clearProperty(NAMESPACE + key);
        }
    }

    private static void addBootstrapProperty(String key, long value) {
        addBootstrapProperty(key, String.valueOf(value));
    }

    private static void addBootstrapProperty(String key, String value) {
        System.setProperty(NAMESPACE + key, value);
    }

}
