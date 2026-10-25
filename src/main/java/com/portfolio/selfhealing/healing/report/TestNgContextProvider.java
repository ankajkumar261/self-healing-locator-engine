package com.portfolio.selfhealing.healing.report;

import com.portfolio.selfhealing.healing.core.TestContextProvider;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.Reporter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;

/** The running TestNG method, test or configuration, on this thread. */
public final class TestNgContextProvider implements TestContextProvider {

    @Override
    public Optional<TestInfo> current() {
        ITestResult result = Reporter.getCurrentTestResult();
        if (result == null || result.getMethod() == null) {
            return Optional.empty();
        }
        ITestNGMethod method = result.getMethod();
        return Optional.of(new TestInfo(
                method.getRealClass().getSimpleName(),
                method.getMethodName(),
                method.isTest(),
                new LinkedHashSet<>(Arrays.asList(method.getGroups()))));
    }
}
