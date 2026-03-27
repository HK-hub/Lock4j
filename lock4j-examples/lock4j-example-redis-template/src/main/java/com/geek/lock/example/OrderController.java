package com.geek.lock.example;

import com.geek.lock.annotation.Lock;
import com.geek.lock.core.AbstractKeyBuilder;
import com.geek.lock.enums.KeyAbsentPolicy;
import com.geek.lock.redis.annotation.RedisTemplateLock;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

@RestController
@RequestMapping("/api")
public class OrderController {

    @Lock(keys = "#orderId", leaseTime = 60000)
    @GetMapping("/order/{orderId}/process")
    public String processOrder(@PathVariable String orderId) {
        return "Processing order: " + orderId;
    }

    @RedisTemplateLock(keys = "#userId", waitTime = 5000, leaseTime = 30000)
    @GetMapping("/user/{userId}/deduct")
    public String deductBalance(@PathVariable String userId) {
        return "Deducting balance for user: " + userId;
    }

    @Lock(keys = {"#productId", "#warehouseId"}, waitTime = 3000, leaseTime = 30000)
    @GetMapping("/product/{productId}/warehouse/{warehouseId}/stock")
    public String deductStock(@PathVariable String productId, @PathVariable String warehouseId) {
        return "Deducting stock for product: " + productId + " in warehouse: " + warehouseId;
    }

    @Lock(keyBuilder = OrderKeyBuilder.class, leaseTime = 60000)
    @GetMapping("/order/custom/{id}/{userId}")
    public String processOrderByCustomKey(@PathVariable String id, @PathVariable String userId) {
        return "Processing order with custom key builder";
    }

    @Lock(leaseTime = 60000)
    @GetMapping("/order/auto")
    public String processOrderByMethodPath() {
        return "Processing order with auto generated key (method path)";
    }

    @Lock(keyAbsentPolicy = KeyAbsentPolicy.THROW_EXCEPTION, leaseTime = 60000)
    @GetMapping("/order/strict")
    public String processOrderStrict() {
        return "This will throw exception because no key is specified";
    }
}

@Component
class OrderKeyBuilder extends AbstractKeyBuilder {

    @Override
    protected String[] doBuild(Method method, String[] parameterNames, Object[] args, Lock annotation) {
        if (args.length >= 2) {
            return new String[]{"order:" + args[0] + ":" + args[1]};
        }
        return new String[0];
    }
}