package org.jboss.logmanager;

import static java.security.AccessController.doPrivileged;

import java.lang.module.ModuleDescriptor;
import java.security.PrivilegedAction;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jboss.modules.Module;
import org.jboss.modules.Version;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class JDKSpecific {

    static final StackWalker WALKER = doPrivileged(new GetStackWalkerAction());

    private JDKSpecific() {}

    private static final boolean JBOSS_MODULES;

    static {
        boolean jbossModules = false;
        try {
            Module.getStartTime();
            jbossModules = true;
        } catch (Throwable ignored) {}
        JBOSS_MODULES = jbossModules;
    }

    static Class<?> findCallingClass(Set<ClassLoader> rejectClassLoaders) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            return doPrivileged(new FindCallingClassAction(rejectClassLoaders));
        } else {
            return WALKER.walk(new FindFirstWalkFunction(rejectClassLoaders));
        }
    }

    static void calculateCaller(ExtLogRecord logRecord) {
        WALKER.walk(new CallerCalcFunction(logRecord));
    }

    static void calculateJdkModule(final ExtLogRecord logRecord, final Class<?> clazz) {
        final java.lang.Module module = clazz.getModule();
        if (module != null) {
            logRecord.setSourceModuleName(module.getName());
            final ModuleDescriptor descriptor = module.getDescriptor();
            if (descriptor != null) {
                final Optional<ModuleDescriptor.Version> optional = descriptor.version();
                if (optional.isPresent()) {
                    logRecord.setSourceModuleVersion(optional.get().toString());
                } else {
                    logRecord.setSourceModuleVersion(null);
                }
            }
        }
    }

    static void calculateModule(final ExtLogRecord logRecord, final Class<?> clazz) {
        final Module module = Module.forClass(clazz);
        if (module != null) {
            logRecord.setSourceModuleName(module.getName());
            final Version version = module.getVersion();
            if (version != null) {
                logRecord.setSourceModuleVersion(version.toString());
            } else {
                logRecord.setSourceModuleVersion(null);
            }
        } else {
            calculateJdkModule(logRecord, clazz);
        }
    }

    static final class CallerCalcFunction implements Function<Stream<StackWalker.StackFrame>, Void> {
        private final ExtLogRecord logRecord;

        CallerCalcFunction(final ExtLogRecord logRecord) {
            this.logRecord = logRecord;
        }

        public Void apply(final Stream<StackWalker.StackFrame> stream) {
            final String loggerClassName = logRecord.getLoggerClassName();
            final Iterator<StackWalker.StackFrame> iterator = stream.iterator();
            boolean found = false;
            while (iterator.hasNext()) {
                final StackWalker.StackFrame frame = iterator.next();
                final Class<?> clazz = frame.getDeclaringClass();
                if (clazz.getName().equals(loggerClassName)) {
                    // next entry could be the one we want!
                    found = true;
                } else if (found) {
                    logRecord.setSourceClassName(frame.getClassName());
                    logRecord.setSourceMethodName(frame.getMethodName());
                    logRecord.setSourceFileName(frame.getFileName());
                    logRecord.setSourceLineNumber(frame.getLineNumber());
                    if (JBOSS_MODULES) {
                        calculateModule(logRecord, clazz);
                    } else {
                        calculateJdkModule(logRecord, clazz);
                    }
                    return null;
                }
            }
            logRecord.setUnknownCaller();
            return null;
        }
    }

    static final class GetStackWalkerAction implements PrivilegedAction<StackWalker> {
        GetStackWalkerAction() {}

        public StackWalker run() {
            return StackWalker.getInstance(EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE));
        }
    }

    static final class FindCallingClassAction implements PrivilegedAction<Class<?>> {
        private final Set<ClassLoader> rejectClassLoaders;

        FindCallingClassAction(final Set<ClassLoader> rejectClassLoaders) {
            this.rejectClassLoaders = rejectClassLoaders;
        }

        public Class<?> run() {
            return WALKER.walk(new FindFirstWalkFunction(rejectClassLoaders));
        }
    }

    static final class FindFirstWalkFunction implements Function<Stream<StackWalker.StackFrame>, Class<?>> {
        private final Set<ClassLoader> rejectClassLoaders;

        FindFirstWalkFunction(final Set<ClassLoader> rejectClassLoaders) {
            this.rejectClassLoaders = rejectClassLoaders;
        }

        public Class<?> apply(final Stream<StackWalker.StackFrame> stream) {
            final Iterator<StackWalker.StackFrame> iterator = stream.iterator();
            while (iterator.hasNext()) {
                final Class<?> clazz = iterator.next().getDeclaringClass();
                final ClassLoader classLoader = clazz.getClassLoader();
                if (! rejectClassLoaders.contains(classLoader)) {
                    return clazz;
                }
            }
            return null;
        }
    }
}
