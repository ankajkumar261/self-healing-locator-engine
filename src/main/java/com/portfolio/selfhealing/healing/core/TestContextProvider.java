package com.portfolio.selfhealing.healing.core;

import java.util.Optional;
import java.util.Set;

/**
 * Which test is running, without the core depending on a test framework.
 */
public interface TestContextProvider {

    TestContextProvider NONE = Optional::empty;

    Optional<TestInfo> current();

    /**
     * @param className  the test class
     * @param methodName the test or configuration method
     * @param testMethod false while a setup method (e.g. a class-level login) runs
     * @param groups     the method's groups
     */
    record TestInfo(String className, String methodName, boolean testMethod, Set<String> groups) {

        public String displayName() {
            return className + "." + methodName;
        }
    }
}
