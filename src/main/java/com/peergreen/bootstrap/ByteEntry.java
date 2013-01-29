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

import java.security.CodeSource;

/**
 * The ByteEntry store the bytecode of a class and its associated codesource.
 * By using a codesource we can define classes with it and then classes can get their URL location
 * @author Florent Benoit
 */
public class ByteEntry {

    /**
     * Codesource of this entry.
     */
    private final CodeSource codesource;

    /**
     * Bytes of this entry.
     */
    private final byte[] bytes;

    /**
     * Defines a new entry
     * @param codeSource the associated codesource
     * @param bytes the array of bytes that is containing the class bytecode
     */
    public ByteEntry(CodeSource codeSource, byte[] bytes) {
        this.codesource = codeSource;
        this.bytes = bytes.clone();
    }

    /**
     * @return the codesource
     */
    public CodeSource getCodesource() {
        return codesource;
    }

    /**
     * @return the bytecode of the class to define
     */
    public byte[] getBytes() {
        return bytes.clone();
    }

}
