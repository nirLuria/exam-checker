package com.examchecker.api;

import com.examchecker.application.CheckService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/check")
public class CheckController {

    private final CheckService checkService;

    public CheckController(CheckService checkService) {
        this.checkService = checkService;
    }

    @PostMapping
    public Map<String, Object> check(@RequestParam("file") MultipartFile file) {
        return checkService.check(file);
    }
}