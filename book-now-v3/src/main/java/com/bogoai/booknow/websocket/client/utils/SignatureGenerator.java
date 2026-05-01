package com.bogoai.booknow.websocket.client.utils;

public interface SignatureGenerator {
    String getSignature(String payload);
}
