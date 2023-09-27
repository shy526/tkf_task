package com.github.shy526.thread;


import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 创建线程池帮助类
 *
 * @author shy526
 */
public class ThreadPoolUtils {


    /**
     * 创建线程池
     *
     * @return ThreadPoolExecutor
     */
    public static ExecutorService getThreadPool() {
        return ThreadPoolUtils.getThreadPool(null, null);
    }

    /**
     * 自定义线程池属性
     *
     * @param threadPoolConfig 属性
     * @return ThreadPoolExecutor
     */
    public static ExecutorService getThreadPool(ThreadPoolConfig threadPoolConfig) {
        return ThreadPoolUtils.getThreadPool(null, threadPoolConfig);
    }

    /**
     * 自定义任务前戳
     *
     * @param beforeName 前戳
     * @return ThreadPoolExecutor
     */
    public static ExecutorService getThreadPool(String beforeName) {
        return ThreadPoolUtils.getThreadPool(beforeName, null);
    }


    /**
     * 创建指定前戳的任务名称和配置
     *
     * @param beforeName       前戳名称
     * @param threadPoolConfig 线程池配置 为null时自动创建
     * @return ThreadPoolExecutor
     */
    public static ExecutorService getThreadPool(String beforeName, ThreadPoolConfig threadPoolConfig) {
        NamedThreadFactory namedThreadFactory = getNamedThreadFactory(beforeName, false);
        if (threadPoolConfig == null) {
            threadPoolConfig = threadPoolConfigBuild();
        }
        return new ThreadPoolExecutor(threadPoolConfig.getCorePoolSize(), threadPoolConfig.getMaximumPoolSizeSize(), threadPoolConfig.getKeepAliveTime(),
                TimeUnit.SECONDS,
                threadPoolConfig.getWorkQueue(), namedThreadFactory, threadPoolConfig.getRejectedExecutionHandler());
    }


    /**
     * 获取一个SingleThreadExecutor
     *
     * @param beforeName 任务前戳
     * @return ThreadPoolExecutor
     */
    public static ExecutorService getSingleExecutor(String beforeName) {
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();
        threadPoolConfig.setCorePoolSize(1);
        threadPoolConfig.setMaximumPoolSizeSize(1);
        threadPoolConfig.setKeepAliveTime(0L);
        threadPoolConfig.setWorkQueue(new LinkedBlockingQueue<>());
        return getThreadPool(beforeName, threadPoolConfig);
    }


    /**
     * 定时器线程池
     *
     * @param beforeName   beforeName 任务前戳
     * @param corePoolSize 核心线程池数
     * @return ScheduledThreadPoolExecutor
     */
    public static ScheduledExecutorService getScheduledExecutor(String beforeName, int corePoolSize) {
        return getScheduledExecutor(beforeName, corePoolSize, false);
    }

    /**
     * 定时器线程池
     *
     * @param beforeName   beforeName 任务前戳
     * @param corePoolSize 核心线程池数
     * @return ScheduledThreadPoolExecutor
     */
    public static ScheduledExecutorService getScheduledExecutor(String beforeName, int corePoolSize, boolean daemon) {
        NamedThreadFactory namedThreadFactory = getNamedThreadFactory(beforeName, daemon);
        return new ScheduledThreadPoolExecutor(corePoolSize, namedThreadFactory);
    }

    /**
     * 获取线程命名工厂
     *
     * @param beforeName beforeName 任务前戳
     * @param daemon     daemon 是否为守护线程
     * @return NamedThreadFactory
     */
    private static NamedThreadFactory getNamedThreadFactory(String beforeName, boolean daemon) {
        NamedThreadFactory namedThreadFactory = null;
        if ("".equals(beforeName) || beforeName == null) {
            namedThreadFactory = new NamedThreadFactory();
        } else {
            namedThreadFactory = new NamedThreadFactory(beforeName, daemon);
        }
        return namedThreadFactory;
    }


    /***
     * 自定义线程命名工厂
     */
    private static class NamedThreadFactory implements ThreadFactory {
        /**
         * 任务名称前戳
         */
        private String beforeName = "task";
        private final AtomicInteger threadNumberAtomicInteger = new AtomicInteger(1);
        private boolean daemon;

        public NamedThreadFactory(String beforeName, boolean daemon) {
            this.beforeName = beforeName;
            this.daemon = daemon;
        }

        public NamedThreadFactory() {
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, String.format(Locale.CHINA, "%s-%d", this.beforeName, threadNumberAtomicInteger.getAndIncrement()));
            //是否是守护线程
            thread.setDaemon(daemon);
            //设置优先级 1~10 有3个常量 默认 Thread.MIN_PRIORITY*/
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }

    /**
     * 获取一个默认的线程池配置
     *
     * @return ThreadPoolConfig
     */
    public static ThreadPoolConfig threadPoolConfigBuild() {
        return new ThreadPoolConfig();
    }


    /**
     * 线程池参数
     */
    public static class ThreadPoolConfig {
        /**
         * 线程池的基本大小
         */
        private int corePoolSize = Runtime.getRuntime().availableProcessors() + 1;
        /**
         * 线程池最大数量
         */
        private int maximumPoolSizeSize = 100;
        /**
         * 线程活动保持时间
         */
        private long keepAliveTime = 60;
        /**
         * 任务队列
         */
        private BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(10);

        /**
         * 拒绝策略
         * AbortPolicy 丢弃任务，并抛出拒绝执行 RejectedExecutionException 异常信息
         * DiscardPolicy 该策略默默的丢弃无法处理的任务，不予任何处理
         * DiscardOldestPolicy 丢弃阻塞队列 workQueue 中最老的一个任务，并将新任务加入
         * CallerRunsPolicy 只要线程池没有关闭的话，则使用调用线程直接运行任务,执行程序已关闭，则会丢弃该任务
         */
        private RejectedExecutionHandler rejectedExecutionHandler = new ThreadPoolExecutor.CallerRunsPolicy();

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaximumPoolSizeSize() {
            return maximumPoolSizeSize;
        }

        public void setMaximumPoolSizeSize(int maximumPoolSizeSize) {
            this.maximumPoolSizeSize = maximumPoolSizeSize;
        }

        public long getKeepAliveTime() {
            return keepAliveTime;
        }

        public void setKeepAliveTime(long keepAliveTime) {
            this.keepAliveTime = keepAliveTime;
        }

        public BlockingQueue<Runnable> getWorkQueue() {
            return workQueue;
        }

        public void setWorkQueue(BlockingQueue<Runnable> workQueue) {
            this.workQueue = workQueue;
        }

        public RejectedExecutionHandler getRejectedExecutionHandler() {
            return rejectedExecutionHandler;
        }

        public void setRejectedExecutionHandler(RejectedExecutionHandler rejectedExecutionHandler) {
            this.rejectedExecutionHandler = rejectedExecutionHandler;
        }
    }

}