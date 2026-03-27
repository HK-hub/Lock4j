package com.geek.lock.example;

import com.geek.lock.annotation.Lock;
import com.geek.lock.core.AbstractKeyBuilder;
import com.geek.lock.enums.KeyAbsentPolicy;
import com.geek.lock.enums.LockType;
import com.geek.lock.redisson.annotation.RedissonLock;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

@RestController
@RequestMapping("/api")
public class OrderController {

    /**
     * 使用 SpEL 表达式作为 Key
     */
    @Lock(keys = "#orderId", leaseTime = 60000)
    @GetMapping("/order/{orderId}/process")
    public String processOrder(@PathVariable String orderId) {
        return "Processing order: " + orderId;
    }

    /**
     * 使用公平锁
     */
    @RedissonLock(keys = "#userId", lockType = LockType.FAIR, waitTime = 5000)
    @GetMapping("/user/{userId}/deduct")
    public String deductBalance(@PathVariable String userId) {
        return "Deducting balance for user: " + userId;
    }

    /**
     * 多个 Key 同时加锁
     */
    @Lock(keys = {"#productId", "#warehouseId"}, waitTime = 3000, leaseTime = 30000)
    @GetMapping("/product/{productId}/warehouse/{warehouseId}/stock")
    public String deductStock(@PathVariable String productId, @PathVariable String warehouseId) {
        return "Deducting stock for product: " + productId + " in warehouse: " + warehouseId;
    }

    /**
     * 使用自定义 KeyBuilder
     */
    @Lock(keyBuilder = OrderKeyBuilder.class, leaseTime = 60000)
    @GetMapping("/order/custom/{id}/{userId}")
    public String processOrderByCustomKey(@PathVariable String id, @PathVariable String userId) {
        return "Processing order with custom key builder";
    }

    /**
     * 不指定 keys 和 keyBuilder，使用方法全限定名作为 Key
     * 默认 keyAbsentPolicy = USE_METHOD_PATH
     */
    @Lock(leaseTime = 60000)
    @GetMapping("/order/auto")
    public String processOrderByMethodPath() {
        return "Processing order with auto generated key (method path)";
    }

    /**
     * 不指定 keys 和 keyBuilder，抛出异常
     * keyAbsentPolicy = THROW_EXCEPTION
     */
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