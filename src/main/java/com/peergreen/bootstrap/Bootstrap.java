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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Set;

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
     * If this System property is set to {@literal true}, the Bootstrap will only print a report on remaining thread when bootstap start() method is done.
     * if false, stop of these threads is performed.
     */
    private static final String ONLY_REPORTING_REMAINING_THREADS_ON_STOP = "com.peergreen.bootstrap.thread.check.reportonly";

    /**
     * Keep arguments of the bootstrap in order to send them to the delegating class.
     */
    private final String[] args;

    /**
     * ClassLoader built in order to load sub-jars.
     */
    private ClassLoader classLoader;

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
        Class<?> mainClass = load();

        // Gets main method
        Method mainMethod = null;
        try {
            mainMethod = mainClass.getMethod("main",String[].class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new BootstrapException("The delegating class '" + mainClass.getName() + "' to launch has no main(String[] args) method available.", e);
        }

        addBootstrapProperty("main.invoke", System.currentTimeMillis());
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        // Call main
        try {
            mainMethod.invoke(null, (Object) args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new BootstrapException("Unable to call the main method of the delegating class '" + mainClass.getName() + "'.", e);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }


    }

    /**
     * Starts the bootstrap by invoking the main method of the delegating class
     * @throws BootstrapException if there is a failure.
     */
    public Class<?> load() throws BootstrapException {

        // Gets the location of the jar containing this class
        URL url = getLocation();

        // Scan entries in our all-in-one jar.
        EntriesRepository entriesRepository = new EntriesRepository(url);

        // Register our URL factory
        try {
            URL.setURLStreamHandlerFactory(new BootstrapURLStreamHandlerFactory(entriesRepository));
        } catch (Error e) {
            // already set so reset field

            // try to reset field
            try {
                Field f = URL.class.getDeclaredField("factory");
                f.setAccessible(true);
                f.set(null,  null);
                URL.setURLStreamHandlerFactory(new BootstrapURLStreamHandlerFactory(entriesRepository));
            } catch (Error | IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException error) {
                throw new BootstrapException("Unable to set the URL Stream handler factory", error);
            }
        }

        // Scan entries
        long t0 = System.currentTimeMillis();
        entriesRepository.scan();
        long tEnd = System.currentTimeMillis();
        addBootstrapProperty("scan.begin", t0);
        addBootstrapProperty("scan.end", tEnd);

        // Create classloader with embedded jars
        this.classLoader = getClassLoader(entriesRepository);

        //FIXME: Allows to specify class to load
        // Class to load
        String classname = "com.peergreen.kernel.launcher.Kernel";

        // Load delegating class
        try {
            return classLoader.loadClass(classname);
        } catch (ClassNotFoundException e) {
            throw new BootstrapException("Unable to load the class '" + classname + "'.", e);
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
        Bootstrap bootstrap = newBootstrap(args);
        try {
            bootstrap.start();
        } catch (BootstrapException e) {
            e.printStackTrace(System.err);
            exception = true;
        } finally {
            bootstrap.terminate(exception);
        }
    }

    @SuppressWarnings("deprecation")
    private void terminate(boolean exception) {
        clearBootstrapProperties("begin", "scan.begin", "scan.end", "main.invoke");

        // Get threads
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        if (threads != null) {
            for (Thread thread : threads) {

                if (isChildCreatedThread(thread) && !thread.isDaemon()) {
                    boolean onlyReporting = Boolean.getBoolean(ONLY_REPORTING_REMAINING_THREADS_ON_STOP);
                    System.err.println(String.format("WARN: The thread '%s' is not a daemon thread and is still running.", thread.getName()));
                    if (!onlyReporting) {
                        System.err.println(String.format("WARN: Stopping thread '%s' for a clean shutdown of the bootstrap. Add System.property -D%s=true for only printing the report on remaining threads.", thread.getName(), ONLY_REPORTING_REMAINING_THREADS_ON_STOP));
                        thread.stop();
                    }
                }
            }
        }

        if (Boolean.getBoolean(SYSTEM_EXIT_ENABLED_PROPERTY)) {
            System.exit(exception ? -1 : 0);
        }

    }

    /**
     * Check if the given thread has been created by a child classloader.
     * @param thread the thread to check
     * @return true if the thread is a child thread
     */
    private boolean isChildCreatedThread(Thread thread) {
        if (classLoader == null) {
            return false;
        }
        ClassLoader cl = thread.getContextClassLoader();
        while (cl != null) {
            cl = cl.getParent();
            // matching classloader
            if (classLoader.equals(cl)) {
                return true;
            }
        }
        return false;
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


    public static Bootstrap newBootstrap(String[] args) {
        Bootstrap bootstrap = new Bootstrap(args);
        return bootstrap;
    }

}
