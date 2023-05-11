package org.jboss.logmanager.formatters;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Handler;

import org.jboss.logmanager.ExtFormatter;

/**
 * A formatter which prints a text banner ahead of the normal formatter header.
 * The text banner is acquired from a {@link Supplier} which is passed in to the constructor.
 * Several utility methods are also present which allow easy creation of {@code Supplier} instances.
 */
public final class TextBannerFormatter extends ExtFormatter.Delegating {
    private final Supplier<String> bannerSupplier;

    /**
     * Construct a new instance.
     *
     * @param bannerSupplier the supplier for the banner (must not be {@code null})
     * @param delegate       the delegate formatter (must not be {@code null})
     */
    public TextBannerFormatter(final Supplier<String> bannerSupplier, final ExtFormatter delegate) {
        super(delegate);
        this.bannerSupplier = Objects.requireNonNull(bannerSupplier, "bannerSupplier");
    }

    // doc inherited
    public String getHead(final Handler h) {
        final String dh = Objects.requireNonNullElse(delegate.getHead(h), "");
        final String banner = Objects.requireNonNullElse(bannerSupplier.get(), "");
        return banner + dh;
    }

    /**
     * Get the empty supplier which always returns an empty string.
     *
     * @return the empty supplier (not {@code null})
     */
    public static Supplier<String> getEmptySupplier() {
        return EMPTY;
    }

    /**
     * Create a supplier which always returns the given string.
     *
     * @param string the string (must not be {@code null})
     * @return a supplier which returns the given string (not {@code null})
     */
    public static Supplier<String> createStringSupplier(String string) {
        Objects.requireNonNull(string, "string");
        return () -> string;
    }

    /**
     * Create a supplier which loads the banner from the given file path,
     * falling back to the given fallback supplier on error.
     *
     * @param path     the path to load from (must not be {@code null})
     * @param fallback the fallback supplier (must not be {@code null})
     * @return the supplier (not {@code null})
     */
    public static Supplier<String> createFileSupplier(Path path, Supplier<String> fallback) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(fallback, "fallback");
        return () -> {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                return fallback.get();
            }
        };
    }

    /**
     * Create a supplier which loads the banner from the given URL,
     * falling back to the given fallback supplier on error.
     *
     * @param url      the URL to load from (must not be {@code null})
     * @param fallback the fallback supplier (must not be {@code null})
     * @return the supplier (not {@code null})
     */
    public static Supplier<String> createUrlSupplier(URL url, Supplier<String> fallback) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(fallback, "fallback");
        return () -> {
            try {
                final InputStream is = url.openStream();
                return is == null ? fallback.get() : loadStringFromStream(is);
            } catch (IOException ignored) {
                return fallback.get();
            }
        };
    }

    /**
     * Create a supplier which loads the banner from a resource in the given class loader,
     * falling back to the given fallback supplier on error.
     *
     * @param resource    the resource name (must not be {@code null})
     * @param classLoader the class loader to load from (must not be {@code null})
     * @param fallback    the fallback supplier (must not be {@code null})
     * @return the supplier (not {@code null})
     */
    public static Supplier<String> createResourceSupplier(String resource, ClassLoader classLoader, Supplier<String> fallback) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(fallback, "fallback");
        return () -> {
            try {
                final InputStream is = classLoader.getResourceAsStream(resource);
                return is == null ? fallback.get() : loadStringFromStream(is);
            } catch (IOException ignored) {
                return fallback.get();
            }
        };
    }

    /**
     * Create a supplier which loads the banner from a resource in the caller's class loader,
     * falling back to the given fallback supplier on error.
     *
     * @param resource the resource name (must not be {@code null})
     * @param fallback the fallback supplier (must not be {@code null})
     * @return the supplier (not {@code null})
     */
    public static Supplier<String> createResourceSupplier(String resource, Supplier<String> fallback) {
        return createResourceSupplier(resource, getClassLoader(STACK_WALKER.getCallerClass()), fallback);
    }

    // private stuff

    private static final Supplier<String> EMPTY = createStringSupplier("");
    private static final StackWalker STACK_WALKER = AccessController.doPrivileged(
            (PrivilegedAction<StackWalker>) () -> StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE));

    private static ClassLoader getClassLoader(Class<?> clazz) {
        return AccessController.doPrivileged((PrivilegedAction<? extends ClassLoader>) clazz::getClassLoader);
    }

    private static String loadStringFromStream(final InputStream is) throws IOException {
        try (is) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
