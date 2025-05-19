package com.wobbz.framework.analytics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class to track performance metrics for a specific hook.
 */
public class HookMetric {
    private final String mHookId;
    private final String mModuleId;
    private final String mTargetPackage;
    
    private final AtomicLong mTotalExecutionTime = new AtomicLong(0);
    private final AtomicLong mMaxExecutionTime = new AtomicLong(0);
    private final AtomicLong mLastExecutionTime = new AtomicLong(0);
    private final AtomicInteger mExecutionCount = new AtomicInteger(0);
    private final AtomicInteger mSuccessCount = new AtomicInteger(0);
    
    private final Map<Long, Long> mStartTimes = new HashMap<>();
    
    /**
     * Create a new hook metric.
     * 
     * @param hookId The hook identifier.
     * @param moduleId The module identifier.
     * @param targetPackage The target package.
     */
    public HookMetric(String hookId, String moduleId, String targetPackage) {
        mHookId = hookId;
        mModuleId = moduleId;
        mTargetPackage = targetPackage;
    }
    
    /**
     * Begin tracking execution time.
     * 
     * @param trackingId Unique tracking ID for this execution.
     */
    public void beginExecution(long trackingId) {
        synchronized (mStartTimes) {
            mStartTimes.put(trackingId, System.nanoTime());
        }
    }
    
    /**
     * End tracking execution time.
     * 
     * @param trackingId The tracking ID from {@link #beginExecution}.
     * @param success Whether the execution succeeded.
     * @return true if this tracking ID was found and processed, false otherwise.
     */
    public boolean endExecution(long trackingId, boolean success) {
        Long startTime;
        synchronized (mStartTimes) {
            startTime = mStartTimes.remove(trackingId);
            if (startTime == null) {
                return false;
            }
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        // Update metrics
        mExecutionCount.incrementAndGet();
        if (success) {
            mSuccessCount.incrementAndGet();
        }
        
        mTotalExecutionTime.addAndGet(duration);
        mLastExecutionTime.set(duration);
        
        // Update max execution time if needed
        long maxTime = mMaxExecutionTime.get();
        while (duration > maxTime) {
            if (mMaxExecutionTime.compareAndSet(maxTime, duration)) {
                break;
            }
            maxTime = mMaxExecutionTime.get();
        }
        
        return true;
    }
    
    /**
     * Get the hook identifier.
     */
    public String getHookId() {
        return mHookId;
    }
    
    /**
     * Get the module identifier.
     */
    public String getModuleId() {
        return mModuleId;
    }
    
    /**
     * Get the target package.
     */
    public String getTargetPackage() {
        return mTargetPackage;
    }
    
    /**
     * Get the total number of executions.
     */
    public int getExecutionCount() {
        return mExecutionCount.get();
    }
    
    /**
     * Get the total number of successful executions.
     */
    public int getSuccessCount() {
        return mSuccessCount.get();
    }
    
    /**
     * Get the success rate (0-100).
     */
    public float getSuccessRate() {
        int executions = mExecutionCount.get();
        if (executions == 0) {
            return 100.0f; // No executions means no failures
        }
        
        return ((float) mSuccessCount.get() / executions) * 100.0f;
    }
    
    /**
     * Get the total execution time (nanoseconds).
     */
    public long getTotalExecutionTime() {
        return mTotalExecutionTime.get();
    }
    
    /**
     * Get the average execution time (nanoseconds).
     */
    public long getAverageExecutionTime() {
        int executions = mExecutionCount.get();
        if (executions == 0) {
            return 0;
        }
        
        return mTotalExecutionTime.get() / executions;
    }
    
    /**
     * Get the maximum execution time (nanoseconds).
     */
    public long getMaxExecutionTime() {
        return mMaxExecutionTime.get();
    }
    
    /**
     * Get the last execution time (nanoseconds).
     */
    public long getLastExecutionTime() {
        return mLastExecutionTime.get();
    }
} 