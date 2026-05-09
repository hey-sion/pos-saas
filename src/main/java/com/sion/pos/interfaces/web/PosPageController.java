package com.sion.pos.interfaces.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PosPageController {

    @GetMapping("/")
    public String pos() {
        return "pos/index";
    }

    @GetMapping("/manage")
    public String manage() {
        return "pos/manage";
    }
}