package com.portfolio.selfhealing.listeners;

import com.portfolio.selfhealing.constants.AppConstants;
import com.portfolio.selfhealing.utils.ConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IAnnotationTransformer;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * RetryListener - Automatically retries failed tests based on retry.count in config.
 */
public class RetryListener implements IAnnotationTransformer {

    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor testConstructor, Method testMethod) {
        // Never retry generation tests: each attempt spends real credits, so a
        // retry would silently double the cost of a flake.
        if (annotation.getGroups() != null
                && Arrays.asList(annotation.getGroups()).contains(AppConstants.GROUP_GENERATION)) {
            return;
        }
        annotation.setRetryAnalyzer(RetryAnalyzer.class);
    }

    // ---- Inner Retry Analyzer ----

    public static class RetryAnalyzer implements IRetryAnalyzer {

        private static final Logger logger = LogManager.getLogger(RetryAnalyzer.class);
        private static final int MAX_RETRY = ConfigReader.getInstance().getRetryCount();
        private int retryCount = 0;

        @Override
        public boolean retry(ITestResult result) {
            if (retryCount < MAX_RETRY) {
                retryCount++;
                logger.warn("Retrying failed test '{}' - Attempt {}/{}",
                        result.getMethod().getMethodName(), retryCount, MAX_RETRY);
                return true;
            }
            return false;
        }
    }
}
