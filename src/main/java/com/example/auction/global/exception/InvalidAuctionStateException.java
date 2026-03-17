package com.example.auction.global.exception;

public class InvalidAuctionStateException extends RuntimeException {

    public InvalidAuctionStateException(String message) {
        super(message);
    }
}

