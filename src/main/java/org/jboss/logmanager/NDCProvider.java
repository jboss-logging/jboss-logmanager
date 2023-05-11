package org.jboss.logmanager;

public interface NDCProvider {

    /**
     * Push a value on to the NDC stack, returning the new stack depth which should later be used to restore the stack.
     *
     * @param context the new value
     * @return the new stack depth
     */
    int push(String context);

    /**
     * Pop the topmost value from the NDC stack and return it.
     *
     * @return the old topmost value
     */
    String pop();

    /**
     * Clear the thread's NDC stack.
     */
    void clear();

    /**
     * Trim the thread NDC stack down to no larger than the given size. Used to restore the stack to the depth returned
     * by a {@code push()}.
     *
     * @param size the new size
     */
    void trimTo(int size);

    /**
     * Get the current NDC stack depth.
     *
     * @return the stack depth
     */
    int getDepth();

    /**
     * Get the current NDC value.
     *
     * @return the current NDC value, or {@code ""} if there is none
     */
    String get();

    /**
     * Provided for compatibility with log4j. Get the NDC value that is {@code n} entries from the bottom.
     *
     * @param n the index
     * @return the value or {@code null} if there is none
     */
    String get(int n);

}
