package com.sion.pos.interfaces.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PosPageController {

    @GetMapping({"/", "/pos"})
    public String pos() {
        return "pos/index";
    }

    // TODO 개발용 목업 확인 경로, 운영 배포 전 삭제
    @GetMapping("/pos/test")
    public String posTest() {
        return "pos/index_test";
    }
}
