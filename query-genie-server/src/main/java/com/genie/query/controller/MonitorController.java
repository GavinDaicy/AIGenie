package com.genie.query.controller;

import com.genie.query.infrastructure.api.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * monitor 存活检测
 */
@RestController
public class MonitorController {

    @GetMapping(value = "/monitor")
    public String monitor(HttpServletResponse response) {
        return ApiResult.success().getMessage();
    }
}
