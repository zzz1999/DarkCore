package io.github.zzz1999.entityreskin.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the public, server-rendered marketing/landing page. */
@Controller
class HomeController {

    @GetMapping("/")
    String index() {
        return "index";
    }
}
