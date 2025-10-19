package com.kg.httpproxyserver;

import com.kg.httpproxyserver.proxy.CustomHttpProxy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HttpProxyServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HttpProxyServerApplication.class, args);
        CustomHttpProxy customHttpProxy = new CustomHttpProxy(8080);
    }
}
