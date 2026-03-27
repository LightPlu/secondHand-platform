package com.example.auction.global.lock.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SynchronizedAuctionLock {

    // 메서드 인자 중 락 키로 사용할 인덱스
    int keyArgIndex();

    // 기능 단위 prefix를 두어 락 키 충돌을 방지
    String keyPrefix() default "lock";
}

