/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager;

/**
 * Allows objects to be locked for modification.
 * <p/>
 * When an object is {@link #protect(Object) protected}, modifications to the object are not allowed. To allow
 * modifications for the object, the {@link #enableAccess(Object)} or the {@link #unprotect(Object)} methods must be
 * invoked.
 * <p/>
 * To protect the object after {@link #enableAccess(Object) enabling} access, invoke the {@link #disableAccess()}
 * access.
 * <p/>
 * Note that {@link #enableAccess(Object) enabling} or {@link #disableAccess() disabling} access only applies to the
 * current thread.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface Protectable {

    /**
     * Protect this object from modifications.
     *
     * @param protectionKey the key used to protect the object.
     *
     * @throws SecurityException if the object is already protected.
     */
    void protect(Object protectionKey) throws SecurityException;

    /**
     * Allows the object to be modified if the {@code protectionKey} matches the key used to {@link
     * #protect(Object) protect} the object.
     *
     * @param protectionKey the key used to protect the object.
     *
     * @throws SecurityException if the object is protected and the key doesn't match.
     */
    void unprotect(Object protectionKey) throws SecurityException;

    /**
     * Enable access to the object for modifications on the current thread.
     *
     * @param protectKey the key used to {@link #protect(Object) protect} modifications.
     */
    void enableAccess(Object protectKey);

    /**
     * Disable previous access to the object for modifications on the current thread.
     */
    void disableAccess();
}
