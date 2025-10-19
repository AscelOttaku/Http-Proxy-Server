package com.kg.httpproxyserver.api;

import com.kg.httpproxyserver.model.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data")
public class DataApi {

    @GetMapping
    public Data getData() {
        return Data.builder()
                .id(1L)
                .name("Test Data")
                .build();
    }

    @PostMapping
    public ResponseEntity<Data> saveData(@RequestBody Data data) {
        if (data != null && data.getTxnId() != null)
            return ResponseEntity.status(HttpStatus.CREATED).body(data);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
