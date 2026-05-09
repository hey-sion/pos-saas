package com.sion.pos.interfaces.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PosPageController {

    @GetMapping({"/", "/pos"})
    public String pos() {
        return "pos/index";
    }

    @GetMapping("/pos/manage")
    public String manage() {
        return "pos/manage";
    }
}