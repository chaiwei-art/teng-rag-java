package com.teng.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Redis 引导器：应用启动前探测本机 Redis，没有就拉起内嵌实例。
 *
 * <p>为什么需要（面试可讲）：演示/开发机往往没有 Redis，但缓存代码不能因此退化成
 * "不可用"。策略是<b>探测 → 内嵌兜底 → 降级</b>三层：</p>
 * <ol>
 *   <li>先探 {@code localhost:6379}（300ms 超时）——已有 Redis 直接复用，不重复起</li>
 *   <li>端口空闲 → 用 embedded-redis 在 6379 拉起内嵌实例（JVM 关闭钩子负责停）</li>
 *   <li>内嵌也失败 → 只告警不中断：RedisAnswerCache 捕获连接异常后自动降级为直连 LLM</li>
 * </ol>
 *
 * <p>刻意放在 {@code RagApplication.main} 里、Spring 容器之前执行：
 * 这样 Redis 连接工厂初始化时端口一定就绪，不依赖任何 Bean 加载顺序的"巧合"。</p>
 */
public final class RedisBootstrap {

    private static final Logger log = LoggerFactory.getLogger(RedisBootstrap.class);

    private static final String HOST = "localhost";
    private static final int PORT = 6379;

    private static RedisServer embedded;

    private RedisBootstrap() {
    }

    /**
     * 确保本机 6379 上有可用 Redis。幂等：已有实例则直接返回。
     */
    public static void ensureLocalRedis() {
        if (isPortOpen()) {
            log.info("[redis] 探测到本机 {}:{} 已有 Redis，直接复用", HOST, PORT);
            return;
        }
        try {
            embedded = RedisServer.newRedisServer().port(PORT).build();
            embedded.start();
            log.info("[redis] 本机无 Redis，已拉起内嵌实例（端口 {}）", PORT);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (embedded != null && embedded.isActive()) {
                        embedded.stop();
                        log.info("[redis] 内嵌实例已停止");
                    }
                } catch (IOException ignored) {
                    // JVM 退出中，尽力而为
                }
            }, "redis-shutdown"));
        } catch (Exception e) {
            // 内嵌失败不阻断启动：缓存层会降级，功能仍可用（只是没有缓存加速）
            log.warn("[redis] 内嵌 Redis 启动失败，问答将降级为不走缓存：{}", e.getMessage());
        }
    }

    private static boolean isPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 300);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
