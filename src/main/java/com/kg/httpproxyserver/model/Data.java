package com.kg.httpproxyserver.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class Data {
    private Long id;
    private String name;
    private Long txnId;
}
