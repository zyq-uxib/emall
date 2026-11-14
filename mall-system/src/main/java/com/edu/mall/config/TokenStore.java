package com.edu.mall.config;

import com.edu.mall.entity.Admin;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenStore {
    private final Map<String, Admin> tokenMap = new ConcurrentHashMap<>();

    public String createToken(Admin admin) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenMap.put(token, admin);
        return token;
    }

    public Admin getAdmin(String token) {
        if (token == null || token.isBlank()) return null;
        return tokenMap.get(token);
    }

    public void removeToken(String token) {
        if (token != null) {
            tokenMap.remove(token);
        }
    }
}
