package com.example.auction.global.lock.aop;

import com.example.auction.global.lock.annotation.ReentrantAuctionLock;
import com.example.auction.global.lock.annotation.SynchronizedAuctionLock;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuctionLockAspect {

    private final ConcurrentMap<String, Object> synchronizedMonitors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock> reentrantLocks = new ConcurrentHashMap<>();

    @Around("@annotation(com.example.auction.global.lock.annotation.SynchronizedAuctionLock)")
    public Object applySynchronizedLock(ProceedingJoinPoint joinPoint) throws Throwable {
        SynchronizedAuctionLock lock = getMethod(joinPoint).getAnnotation(SynchronizedAuctionLock.class);
        String lockKey = buildKey(joinPoint.getArgs(), lock.keyArgIndex(), lock.keyPrefix());
        Object monitor = synchronizedMonitors.computeIfAbsent(lockKey, key -> new Object());

        synchronized (monitor) {
            log.debug("[synchronized-lock] acquired: {}", lockKey);
            return joinPoint.proceed();
        }
    }

    @Around("@annotation(com.example.auction.global.lock.annotation.ReentrantAuctionLock)")
    public Object applyReentrantLock(ProceedingJoinPoint joinPoint) throws Throwable {
        ReentrantAuctionLock lock = getMethod(joinPoint).getAnnotation(ReentrantAuctionLock.class);
        String lockKey = buildKey(joinPoint.getArgs(), lock.keyArgIndex(), lock.keyPrefix());
        ReentrantLock reentrantLock = reentrantLocks.computeIfAbsent(lockKey, key -> new ReentrantLock());

        boolean acquired = false;
        int maxRetries = Math.min(Math.max(lock.maxRetries(), 1), 3);
        try {
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                acquired = reentrantLock.tryLock(lock.timeoutMillis(), TimeUnit.MILLISECONDS);
                if (acquired) {
                    log.debug("[reentrant-lock] acquired: {}, attempt={}", lockKey, attempt);
                    return joinPoint.proceed();
                }
                log.debug("[reentrant-lock] acquire failed: {}, attempt={}/{}", lockKey, attempt, maxRetries);
            }

            throw new IllegalStateException("요청 처리 중입니다. 잠시 후 다시 시도해주세요. (maxRetries=" + maxRetries + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 대기 중 인터럽트가 발생했습니다.", e);
        } finally {
            if (acquired && reentrantLock.isHeldByCurrentThread()) {
                reentrantLock.unlock();
            }
            if (!reentrantLock.isLocked() && !reentrantLock.hasQueuedThreads()) {
                reentrantLocks.remove(lockKey, reentrantLock);
            }
        }
    }

    private Method getMethod(ProceedingJoinPoint joinPoint) {
        return ((MethodSignature) joinPoint.getSignature()).getMethod();
    }

    private String buildKey(Object[] args, int keyArgIndex, String keyPrefix) {
        if (keyArgIndex < 0 || keyArgIndex >= args.length) {
            throw new IllegalArgumentException("락 키 인덱스가 유효하지 않습니다. keyArgIndex=" + keyArgIndex);
        }
        return keyPrefix + ":" + String.valueOf(args[keyArgIndex]);
    }
}
