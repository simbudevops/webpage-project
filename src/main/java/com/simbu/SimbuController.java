package com.simbu;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SimbuController {

    // Root URL → redirect to Raegan login (Page 1)
    @GetMapping("/")
    public String home() {
        return "redirect:/raegan.html";
    }

    @GetMapping("/raegan.html")
    public String raegan() {
        return "forward:/raegan.html";
    }

    @GetMapping("/dashboard.html")
    public String dashboard() {
        return "forward:/dashboard.html";
    }

    @GetMapping("/index.html")
    public String simbuHub() {
        return "forward:/index.html";
    }
}
