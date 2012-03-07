/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
